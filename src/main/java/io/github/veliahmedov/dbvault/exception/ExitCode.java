package io.github.veliahmedov.dbvault.exception;

// this will replace all numbered results for ops in cli command classes
public enum ExitCode {
    SUCCESS(0),              //ops complete
    GENERAL_ERROR(1),        // general app failure
    USAGE_ERROR(2),          // Invalid cli arg, Picocli returns for bad arguments
    CORRUPTED_BACKUP(3),     // backup integrity checkup failed
    RESTORE_CANCELLED(4);    // user canceled restoration

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
