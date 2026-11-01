package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.config.DatabaseConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class SqliteAdapter implements DatabaseAdapter{

    // to get host, port, database name, username and password to use in ops
    private final DatabaseConfig  config;

    public SqliteAdapter(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void testConnection() {

    }

    @Override
    public void backup(OutputStream out) throws IOException {

    }

    @Override
    public void restore(InputStream in, List<String> tables) throws IOException {

    }

    @Override
    public List<String> listTables() {
        return List.of();
    }
}
