package dev.omar.kira.shell;

import com.yn.shappky.shell.shizuku.ShizukuShellExecutor;
import java.util.concurrent.CompletableFuture;

public final class ShellExecutors {

    public static ShellResult newShizukuExecutor(String cmd) {
        return new ShizukuShellExecutor().execute(cmd);
    }
}
