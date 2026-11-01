package io.github.veliahmedov.dbvault.exception;

public class DbVaultException extends RuntimeException {
    public DbVaultException(String message) {
        super(message);
    }

    public DbVaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
