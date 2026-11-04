package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.config.DatabaseConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;

//utility method that create adapter based on DB
public class AdapterFactory {

    private AdapterFactory() {}

    //centralize adapter creation instead of every time explicit initiation
    //without we will need selection logic in each of cli
    public static DatabaseAdapter create(DatabaseConfig config) {
        if (config == null || config.type() == null) {
            throw new DbVaultException("Config database type is missing");
        }
        return switch (config.type()) {
            case POSTGRES -> {
                PostgresErrors errors = new PostgresErrors(
                        config.username(), config.name(), config.container());
                yield new PostgresAdapter(config, errors);
            }
            case SQLITE -> throw new DbVaultException("SQLite is not supported yet (planned)");
        };
    }
}
