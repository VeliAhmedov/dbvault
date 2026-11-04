package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.config.DatabaseConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class PostgresAdapter implements DatabaseAdapter{
    // to get host, port, database name, username, container and password to use in ops
    private final DatabaseConfig config;

    private final PostgresErrors errors;

    public PostgresAdapter(DatabaseConfig config, PostgresErrors errors) {
        this.config = config;
        //to reuse same instance for all
        this.errors = new PostgresErrors(config.username(), config.name(), config.container()); // pass original ones
    }

    private Map<String, String> env(){
        Map<String, String> env = new HashMap<>();
        if (config.password() != null && !config.password().isBlank()) {
            env.put("PGPASSWORD", config.password());
        }
        return env;
    }

    private List<String> command(String tool, String... extra){
        List<String> cmd = new ArrayList<>();
        if (useDocker()) {
            // "-e PGPASSWORD" with no value copies it from our environment
            cmd.addAll(List.of("docker", "exec", "-i", "-e", "PGPASSWORD",
                    config.container(), tool, "-U", config.username(), "-w"));
        }else {
            cmd.addAll(List.of(tool, "-h", host(), "-p", port(), "-U", config.username(), "-w"));
        }
        cmd.addAll(Arrays.asList(extra));
        return cmd;
    }

    @Override
    public void testConnection() {
        requireFields();
        try {
            ProcessRunner.capture(command("psql", "-d", config.name(), "-tAc", "SELECT 1"), env());
        }catch (DbVaultException e){
            throw errors.translate(e);
        }
    }

    @Override
    public void backup(OutputStream out) throws IOException {
        requireFields();
        try {
            ProcessRunner.streamTo(
                    command("pg_dump", "-d", config.name(), "--format=plain", "--no-owner"),
                    env(), out);
        } catch (DbVaultException e) {
            throw errors.translate(e);
        }
    }

    @Override
    public void restore(InputStream in, List<String> tables) throws IOException {
        throw new DbVaultException("Restore not implemented yet (Step 5)");
    }

    @Override
    public List<String> listTables() {
        requireFields();
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY 1";
        try {
            return ProcessRunner.capture(command("psql", "-d", config.name(), "-tAc", sql), env())
                    .lines().filter(l -> !l.isBlank()).toList();
        } catch (DbVaultException e) {
            throw errors.translate(e);
        }
    }

    //utility method checking existence of container
    private boolean useDocker(){
        return config.container() != null && !config.container().isBlank();
    }
    // if user doesn't give
    private String host (){
        return config.host() == null ? "localhost" : config.host();
    }
    private String port(){
        return String.valueOf(config.port() == null ? 5432 : config.port());
    }

    private void requireFields(){
        if (config.name() == null || config.name().isBlank())
            throw new DbVaultException("Config database 'name' is required");
        if (config.username() == null || config.username().isBlank())
            throw new DbVaultException("Config database 'username' is required");
    }
}
