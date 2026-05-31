package dev.omar.kira.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.content.ServiceConnection;

import android.widget.Toast;
import com.yn.shappky.shell.ShellExecutors;
import com.yn.shappky.shell.ShellResult;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.yn.shappky.IShizukuShellService;
import com.yn.shappky.shizuku.ShizukuShellService;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.Shell.Result;

import rikka.shizuku.Shizuku;

public class ShellManager {
    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private Boolean hasRoot; // Null indicates not checked yet

    @SuppressWarnings("deprecation")
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener; // Only for Shizuku

    private final Shizuku.UserServiceArgs shizukuServiceArgs;
    private final ServiceConnection shizukuServiceConnection;
    private IShizukuShellService shizukuService;

    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this.context = context;
        this.handler = handler;
        this.executor = executor;
        this.shizukuServiceArgs =
                new Shizuku.UserServiceArgs(new ComponentName(context, ShizukuShellService.class))
                        .processNameSuffix("shizuku_shell_service")
                        .daemon(false);
        this.shizukuServiceConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        shizukuService = IShizukuShellService.Stub.asInterface(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        shizukuService = null;
                    }
                };
    }

    private String getPermissionMode() {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                .getString("permissionMode", "shizuku");
    }

    /** Set the permission listener for Shizuku, if Shizuku is used. */
    public void setShizukuPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        this.shizukuPermissionListener = listener;
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
    }

    /** Remove the Shizuku permission listener. */
    public void removeShizukuPermissionListener() {
        if (shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        }
    }

    /**
     * Check if the device has root access. Caches the result. This method blocks until the check is
     * complete.
     */
    public boolean hasRootAccess() {
        if (hasRoot == null) {
            try {
                hasRoot = Shell.getShell().isRoot();
            } catch (Exception e) {
                hasRoot = false;
            }
        }
        return hasRoot;
    }

    /** Check if Shizuku is available and has necessary permissions. */
    public boolean hasShizukuPermission() {
        return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check for shell permissions (Root first, then Shizuku). If root is available, no further
     * action is needed for permissions. If root is not available, it attempts to check/request
     * Shizuku permission.
     */
    public void checkShellPermissions() {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if ("shizuku".equals(getPermissionMode())) {
                    Shizuku.requestPermission(0); // Request Shizuku permission if not granted
                }
            } else {
                if ("shizuku".equals(getPermissionMode())) {
                    bindShizukuService();
                }
            }
        }
    }

    /** Check if any shell permission (Root or Shizuku) is available. */
    public boolean hasAnyShellPermission() {
        String mode = getPermissionMode();
        if ("shizuku".equals(mode)) {
            return hasShizukuPermission();
        }
        return hasRootAccess();
    }

    public void bindShizukuService() {
        if (!hasShizukuPermission()) {
            return;
        }
        try {
            Shizuku.bindUserService(shizukuServiceArgs, shizukuServiceConnection);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(context,"Failed to bind user service : "+e.getMessage(),1).show();
        }
    }

    public void unbindShizukuService() {
        try {
            Shizuku.unbindUserService(shizukuServiceArgs, shizukuServiceConnection, true);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        shizukuService = null;
    }

    /**
     * Run a shell command prioritizing Root, then Shizuku. This method executes on the executor
     * thread and posts success/failure to main handler.
     */
    public void runShellCommand(String command, Runnable onSuccess) {
        executor.execute(
                () -> {
                    boolean executed = false;
                    if ("root".equals(getPermissionMode()) && hasRootAccess()) {
                        if (executeRootCommand(command, onSuccess, null)) {
                            executed = true;
                        }
                    }
                    if (!executed
                            && "shizuku".equals(getPermissionMode())
                            && hasShizukuPermission()) {
                        if (executeShizukuCommand(command, onSuccess)) {
                            executed = true;
                        }
                    }

                    if (!executed) {
                        if (onSuccess
                                != null) { // Call onComplete even if no permissions to avoid
                                           // deadlock in some cases
                            handler.post(onSuccess);
                        }
                    }
                });
    }

    /**
     * Run a shell command prioritizing Root, then Shizuku, and process its output line by line.
     * This method executes on the executor thread and posts output to main handler.
     */
    public void runShellCommandWithOutput(String command, Consumer<String> outputProcessor) {
        executor.execute(
                () -> {
                    boolean executed = false;
                    if ("root".equals(getPermissionMode()) && hasRootAccess()) {
                        if (executeRootCommand(command, null, outputProcessor)) {
                            executed = true;
                        }
                    }
                    if (!executed
                            && "shizuku".equals(getPermissionMode())
                            && hasShizukuPermission()) {
                        if (executeShizukuCommandWithOutput(command, outputProcessor)) {
                            executed = true;
                        }
                    }
                });
    }

    /**
     * Run a shell command prioritizing Root, then Shizuku, and return the full output as a String.
     * This method is blocking and should be called from a background thread. Returns null if no
     * permissions or an error occurs.
     */
    public String runShellCommandAndGetFullOutput(String command) {
        if ("root".equals(getPermissionMode()) && hasRootAccess()) {
            return executeRootCommandAndGetFullOutput(command);
        } else if ("shizuku".equals(getPermissionMode()) && hasShizukuPermission()) {
            return executeShizukuCommandAndGetFullOutput(command);
        } else {
            return null;
        }
    }

    // --- Private helper methods for command execution ---

    /**
     * Executes a command using root. Returns true on successful execution (even if command output
     * indicates error), false on unrecoverable error.
     */
    private boolean executeRootCommand(
            String command, Runnable onSuccess, Consumer<String> outputProcessor) {
        try {
            Result result = Shell.cmd(command).exec();
            if (outputProcessor != null) {
                for (String line : result.getOut()) {
                    final String finalLine = line;
                    handler.post(() -> outputProcessor.accept(finalLine));
                }
                for (String line : result.getErr()) {
                    final String finalLine = line;
                    handler.post(() -> outputProcessor.accept("ERROR: " + finalLine));
                }
            }
            if (onSuccess != null) {
                handler.post(onSuccess);
            }
            return result.isSuccess();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Executes a command using Shizuku. Returns true on successful execution, false on
     * unrecoverable error.
     */
    private boolean executeShizukuCommand(String command, Runnable onSuccess) {
        try {
            if (shizukuService == null) {
                return false;
            }
            ShellResult result = ShellExecutors.newShizukuExecutor(command);
            if (onSuccess != null) {
                handler.post(onSuccess);
            }
            if(!result.isSuccess()){
                throw new RemoteException(result.getStderr());
            }
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Executes a command using Shizuku and processes output line by line. Returns true on
     * successful execution, false on unrecoverable error.
     */
    private boolean executeShizukuCommandWithOutput(
            String command, Consumer<String> outputProcessor) {
        try {
            if (shizukuService == null) {
                return false;
            }
            String output = shizukuService.runCommand(command);
            if (output != null) {
                String[] lines = output.split("\\r?\\n");
                for (String line : lines) {
                    final String finalLine = line;
                    handler.post(() -> outputProcessor.accept(finalLine));
                }
            }
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Executes a command using root and returns the full output as a String. This method is
     * blocking.
     */
    private String executeRootCommandAndGetFullOutput(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Result result = Shell.cmd(command).exec();
            for (String line : result.getOut()) {
                output.append(line).append("\n");
            }
            for (String line : result.getErr()) {
                output.append("ERROR: ").append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Executes a command using Shizuku and returns the full output as a String. This method is
     * blocking.
     */
    private String executeShizukuCommandAndGetFullOutput(String command) {
        try {
            if (shizukuService == null) {
                bindShizukuService();
            }
            return shizukuService.runCommand(command);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }
}
