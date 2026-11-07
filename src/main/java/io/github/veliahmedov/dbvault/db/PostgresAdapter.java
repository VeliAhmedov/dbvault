package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.config.DatabaseConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class PostgresAdapter implements DatabaseAdapter{

    // pattern validation check for table names, permits letter, underscore (_), number, dot and dollar sign.
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");

    // to get host, port, database name, username, container and password to use in ops.
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
            // pg_dump (create db backup), -d config.name() (which db to backup), --format=custom ()
            // GZIP already does compress, so disable internal compression via  --compress=0
            ProcessRunner.streamTo(
                    command("pg_dump", "-d", config.name(), "--format=custom", "--compress=0"),
                    env(), out);
        } catch (DbVaultException e) {
            throw errors.translate(e);
        }
    }

    // this method fully restores entire db backup
    // in is backup data already decompressed in restore command and tables are names of tables to restore
    @Override
    public void restore(InputStream in, List<String> tables) throws IOException {
        // check validation of configuration vars
        requireFields();
        // check validation of table names, every requested table against given regex
        for (String t : tables) {
            if (!TABLE_NAME.matcher(t).matches())
                throw new DbVaultException("Invalid table name: " + t);
        }
        // this here decide which restore to perform either fully (empty list means no requested ones) or selective files
        try {
            // --clean (drop existing objects so when recreating restore overwrite them)
            // --if-exists (don't throw error when object doesn't exist)
            // --single-transaction (all success or nothing, if any fail db is left untouched, perform in one transaction)
            // --no-owner (no role error on other machines, don't restore original ownership, make new over it)
            if (tables.isEmpty()) {
                // full restore: stream straight into pg_restore
                ProcessRunner.streamFrom(command("pg_restore", "-d", config.name(),
                        "--clean", "--if-exists", "--no-owner", "--single-transaction"), env(), in);
            } else {
                restoreTables(in, tables);
            }
        } catch (DbVaultException e) {
            throw errors.translate(e);
        }
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
    // if user doesn't give it, return default vaulue
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

    // Selective restore: pg_restore needs  file to apply a filtered list (-L).
    // so in short used for restored of given file instead of entire db files
    // in case of example: dbvault restore backup.dump.gz --table users (we only want to restore users)
    private void restoreTables(InputStream in, List<String> tables) throws IOException {
        //temp files: .dump to hold actual db and .list to hold filtered list of objects to restore
        // pg-restore -L require list of entries and backup file that is why we need them
        Path dump = Files.createTempFile("dbvault-", ".dump");
        Path list = Files.createTempFile("dbvault-", ".list");
        String dumpPath = dump.toString();
        String listPath = list.toString();
        // write backup to temp dump
        try {
            try (OutputStream o = Files.newOutputStream(dump)) { in.transferTo(o); }
            // java and db has different filesystems (java is in D:\Temp, db is in tmp/dbvault)
            // temp dumb will be copied for docker db use, if not using this is skipped
            if (useDocker()) {
                dumpPath = "/tmp/" + dump.getFileName();
                listPath = "/tmp/" + list.getFileName();
                ProcessRunner.capture(List.of("docker", "cp", dump.toString(),
                        config.container() + ":" + dumpPath), Map.of());
            }
            // backup's contents, -l means contents of backup like sequence, constraints and etc
            String contents = ProcessRunner.capture(command("pg_restore", "-l", dumpPath), env());
            // filer according to what we want, like there can be TABLE public users, TABLE public posts, we want only users one
            Files.writeString(list, RestoreList.filter(contents, tables));
            // copied filtered list into Docker
            if (useDocker()) {
                ProcessRunner.capture(List.of("docker", "cp", list.toString(),
                        config.container() + ":" + listPath), Map.of());
            }
            // perform selective restore command, -L is don't restore everything but entries i listed in files
            ProcessRunner.capture(command("pg_restore", "-d", config.name(),
                    "--clean", "--if-exists", "--no-owner", "--single-transaction",
                    "-L", listPath, dumpPath), env());
        } finally {
            // clean up temp files both in docker and computer to prevent accumulation
            if (useDocker()) {
                try {
                    ProcessRunner.capture(List.of("docker", "exec", config.container(),
                            "rm", "-f", "/tmp/" + dump.getFileName(), "/tmp/" + list.getFileName()), Map.of());
                } catch (DbVaultException ignored) { }
            }
            Files.deleteIfExists(dump);
            Files.deleteIfExists(list);
        }
    }
}
// flow of restore:
// user executes: dbvault restore backup.dump.gz --table users
// Restore command: downloads backup and decompress it
// PostgresAdapter.restore() validates and choose types:
// 1. full restore -> streamFrom -> pg-restore
// 2. selective restore -> temp .dump -> pg-restore -l -> RestoreList.filter() -> pg_restore -L
// all into DB
