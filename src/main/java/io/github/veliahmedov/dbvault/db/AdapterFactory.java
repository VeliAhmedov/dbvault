package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.config.DatabaseConfig;

//utility method that create adapter based on DB
public class AdapterFactory {

    private AdapterFactory() {}

    //centralize adapter creation instead of every time explicit initiation
    //without we will need selection logic in each of cli
    public static DatabaseAdapter create(DatabaseConfig config) {
        return switch (config.type()){
            case SQLITE ->  new SqliteAdapter(config);
            case POSTGRES ->  new PostgresAdapter(config);
        };
    }
}
