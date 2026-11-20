package io.github.veliahmedov.dbvault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.Container.ExecResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Integration test: it runs real CLI against Postgres container to check on complete workflow of backup and restore
// workflow test:
// 1. start PostgreSQL container
// 2. create db tables and insert data
// 3. run Dbavult.backup()
// 4. verify generated backup
// 5. drop original tables
// 6. restore backup using DbVault
// 7. query PostgreSQL to verify restored data
// annotation automate:  compose start, create testdb, configure to connect, run commands and clean up, it enables JUnit integration
@Testcontainers
public class BackupRestoreIT {

    // test container to integration test and manage against real docker containers
    // @Container is to manage lifecycle, as specific (PostgreSQLContainer) container given for test
    @Container
    static final PostgreSQLContainer POSTGRES =  new PostgreSQLContainer("postgres:18");

    // temporary test environment, @TempDir provide temporary directory for test
    @TempDir
    Path tempDir;

    // temporary YAML config file and directory where dbvault store backups
    private Path config;
    private Path backups;

    // @BeforeEach runs before every test method
    @BeforeEach
    public void setUp() throws Exception {
        // create path in temp dir
        backups = tempDir.resolve("backups");
        Files.createDirectories(backups);
        config = backups.resolve("dbvault.yml");
        // instead of hardcoding db credentials we take then from running container
        Files.writeString(config, """
                database:
                 type: postgres
                 name: "%s"
                 username: "%s"
                 password: "%s"
                 container: "%s"
                defaultStorage: local
                storages:
                 local:
                  type: local
                  path: "%s"
                """
                .formatted(POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        POSTGRES.getContainerId(),
                        backups.toString().replace('\\', '/')));

        // create dummy test data to work with
        psql("DROP TABLE IF EXISTS users, orders");                       //remove tables if already exist
        psql("CREATE TABLE users(id serial PRIMARY KEY, name text); "     // create users table and insert 2 records
                + "INSERT INTO users(name) VALUES ('josh'),('jack')");
        psql("CREATE TABLE orders(id serial PRIMARY KEY, item text); "    // create orders table wth 2 record
                + "INSERT INTO orders(item) VALUES ('a'),('b')");

        //        users                orders
        //       id name              id  item
        //       1	 josh                1   a
        //       2	 jack               2   b
    }

    //TEST 1: full backup then restore (test complete backup and restore cycle)
    @Test
    void backupAndRestoreFlow() throws Exception {
        // execute actual backup cmd, 0 means success
        assertEquals(0, run("backup"));
        // get backup filename to retrieve generated .dump.gz filename from temp dir
        String backup = latestBackup();
        // execute actual verify cmd, 0 means success
        assertEquals(0, run("verify", backup));
        // delete original tables from db
        psql("DROP TABLE users, orders");
        // execute actual restore cmd, 0 means success, --yes auto confirm restore ops
        assertEquals(0, run("restore", backup, "--yes"));
        // verify again restored data for corruption
        assertEquals("josh,jack", psql("SELECT string_agg(name, ',' ORDER BY id) FROM users"));
        // except 2 order related rows of data to check restoration
        assertEquals("2", psql("SELECT count(*) FROM orders"));
        // except 1 PK key index constraint to check it alongside restore entries as well
        assertEquals("1", psql("SELECT count(*) FROM pg_indexes WHERE indexname = 'users_pkey'"));

        // test success: generate backup, store it locally, verify integrity, restore complete db, alongside actual record and entries
    }

    // TEST2: selective restore : restoring only selected tables of db
    @Test
    void selectiveRestoreBringsBackKeyAndSequenceAndLeavesOtherTablesAlone() throws Exception {
        // create backup and retrieve filename
        assertEquals(0, run("backup"));
        String backup = latestBackup();
        //delete only users, only orders remain
        psql("DROP TABLE users");
        // add values to orders table over already existing one from TEST 1
        psql("INSERT INTO orders(item) VALUES ('c')");
        // current status: users table -> doesn't have records; orders table -> does have 3 records
        assertEquals(0, run("restore", backup, "--table", "users", "--yes"));
        // verify users
        assertEquals("josh,jack", psql("SELECT string_agg(name, ',' ORDER BY id) FROM users"));
        // verify that orders didn't rolled back or corrupted (3rd order added after backup so when restored it should remain 3)
        assertEquals("3", psql("SELECT count(*) FROM orders"));   // not rolled back
        //verify PK key index and sequence
        assertEquals("1", psql("SELECT count(*) FROM pg_indexes WHERE indexname = 'users_pkey'"));
        // user had id 1 and 2, after delete sequence entry deleted too, after restoration 3rd added, we check if id value is 3 to be sure sequence restored too
        assertEquals("3", psql("INSERT INTO users(name) VALUES ('test') RETURNING id")
                .lines().findFirst().orElse(""));
    }

    //TEST 3: to check if backup corruption detection works as intended
    @Test
    void corruptedBackupIsDetectedWithExitCode3() throws Exception {
        assertEquals(0, run("backup")); // run backup return 0 success
        String backup = latestBackup();                 //retrieve generated file

        Path file = backups.resolve(backup);            // locate backup as path
        byte[] bytes = Files.readAllBytes(file);        // read bytes of backup file
        bytes[20] ^= (byte) 0xFF;                       // deliberately corrupt it, change byte number to 20 and perform XOR
        Files.write(file, bytes);                       // write back to disk

        assertEquals(3, run("verify", backup));  // verify it, it should return corruption exit code 3
    }

    // Helper methods

    // this is used to execute SQL commands directly inside PostgreSQL container
    private static String psql(String sql) throws Exception {
        // psql -> start exe, -U -> db username, -d -> db name, -qtA -> quiet, tuples-only, unaligned output, -c -> execute following
        ExecResult result = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
                "-d", POSTGRES.getDatabaseName(), "-qtA", "-c", sql);
        // check if succeeded 0, if not send failure message (3rd param)
        assertEquals(0, result.getExitCode(), result.getStderr());
        return result.getStdout().trim();
    }

    // this is used to execute DbVault itself unlike we execute inside container
    private int run(String... args) {
        // exp: run("backup"); produces arg array equivalent to -> backup -c <temporary-config-path>
        String[] full = Stream.concat(Stream.of(args), Stream.of("-c", config.toString())).toArray(String[]::new);
        // then execute inside main class, as you can see we aren't launching another java process rather invoke directly
        // that s reason why create() extracted from main to use it separately
        return DbVaultCommand.create().execute(full);
    }

    // this lists files in backup dir, filters for .dump.gz files and return matching filename
    private String latestBackup() throws Exception {
        try (Stream<Path> files = Files.list(backups)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".dump.gz"))
                    .findFirst().orElseThrow();
        }
    }
}

