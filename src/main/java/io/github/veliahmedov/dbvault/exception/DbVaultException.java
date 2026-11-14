package io.github.veliahmedov.dbvault.exception;

// constructor chaining used for flexible modification as old codes will still work alongside
public class DbVaultException extends RuntimeException {

    private final ExitCode exitCode;

    public DbVaultException(String message) {
        this(message, ExitCode.GENERAL_ERROR);
    }

    public DbVaultException(String message, Throwable cause) {
        this(message, ExitCode.GENERAL_ERROR, cause);
    }


    public DbVaultException(String message, ExitCode exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public DbVaultException(String message, ExitCode exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public ExitCode exitCode() { return exitCode; }}


