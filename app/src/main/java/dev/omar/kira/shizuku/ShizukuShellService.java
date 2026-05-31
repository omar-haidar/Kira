package dev.omar.kira.shizuku;

import dev.omar.kira.IShizukuShellService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ShizukuShellService extends IShizukuShellService.Stub {
    @Override
    public String runCommand(String command) {
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File("/"));
            process = pb.start();
            final Process proc = process;

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(proc.getInputStream(), stdout, false));
            Thread errThread = new Thread(() -> readStream(proc.getErrorStream(), stderr, true));

            outThread.start();
            errThread.start();

            process.waitFor();
            outThread.join();
            errThread.join();

            if (stdout.length() > 0) {
                output.append(stdout);
            }
            if (stderr.length() > 0) {
                output.append(stderr);
            }
        } catch (IOException | InterruptedException e) {
            output.append("ERROR: ").append(e.getMessage()).append("\n");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return output.toString();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static void readStream(java.io.InputStream stream, StringBuilder output, boolean isError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isError) {
                    output.append("ERROR: ").append(line).append("\n");
                } else {
                    output.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            if (isError) {
                output.append("ERROR: ").append(e.getMessage()).append("\n");
            } else {
                output.append(e.getMessage()).append("\n");
            }
        }
    }
}
