package dev.omar.kira.shell;

public class ShellResult {
    
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public ShellResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
    }

    /**
     * Returns true if the command executed successfully (exit code is 0)
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    // Lazy initialization for errorDetails
    private String errorDetails = null;

    private String getErrorDetails() {
        if (errorDetails == null) {
            errorDetails = (stderr + "\n" + stdout).toLowerCase();
        }
        return errorDetails;
    }

    /**
     * Checks if the error is related to missing UID owner map entry
     */
    private Boolean isUidOwnerMapMissing = null;

    public boolean isUidOwnerMapMissing() {
        if (isUidOwnerMapMissing == null) {
            isUidOwnerMapMissing = getErrorDetails().contains("suidownermap does not have entry for uid");
        }
        return isUidOwnerMapMissing;
    }

    /**
     * Returns true if the command succeeded or if it's the specific UID owner map error
     * (which we treat as non-critical in some cases)
     */
    public boolean isEffectivelySuccess() {
        return isSuccess() || isUidOwnerMapMissing();
    }

    // Getters
    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }
}