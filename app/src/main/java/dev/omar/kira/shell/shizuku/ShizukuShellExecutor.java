package dev.omar.kira.shell.shizuku;

import dev.omar.kira.shell.ShellExecutor;
import dev.omar.kira.shell.ShellResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;


public class ShizukuShellExecutor implements ShellExecutor {
    private static Method newProcessMethod;

    static {
        try {
            newProcessMethod =
                    Shizuku.class.getDeclaredMethod(
                            "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find newProcess method", e);
        }
    }

    @Override
    public ShellResult execute(String command) {
        try {
            Object process =
                    newProcessMethod.invoke(
                            null, new String[] {"/system/bin/sh", "-c", command}, null, null);

            if (!(process instanceof ShizukuRemoteProcess)) {
                return new ShellResult(-1, "", "no-process");
            }

            ShizukuRemoteProcess remoteProcess = (ShizukuRemoteProcess) process;

            // Read stdout
            StringBuilder stdoutBuilder = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(remoteProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutBuilder.append(line).append("\n");
                }
            }

            // Read stderr
            StringBuilder stderrBuilder = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(remoteProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                }
            }

            remoteProcess.getOutputStream().close();
            int exitCode = remoteProcess.waitFor();
            remoteProcess.destroy();

            return new ShellResult(exitCode, stdoutBuilder.toString(), stderrBuilder.toString());
        } catch (Exception e) {
            return new ShellResult(-1, "", e.getMessage() != null ? e.getMessage() : "");
        }
    }
}
