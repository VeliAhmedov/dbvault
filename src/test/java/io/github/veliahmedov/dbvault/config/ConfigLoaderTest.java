package io.github.veliahmedov.dbvault.config;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
// unit tests for checking features of configLoader
public class ConfigLoaderTest {

    private static final String YAML = """
            database:
             type: postgres
             name: testdb
             username: ${DB_USERNAME}
             password: ${DB_PASSWORD}
            defaultStorage: local
            storages:
             local:
              type: local
              path: ./backups
             s3:
              type: s3
              endpoint: http://localhost:3900
              bucket: dbvault-backups
              region: garage
              accessKey: ${S3_ACCESS_KEY}
              secretKey: ${S3_SECRET_KEY}
            """;

    private static final Map<String, String> VARS = Map.of("DB_USERNAME", "jack", "DB_PASSWORD", "secret");

    // temporary test environment
    @TempDir
    Path dir;

    private Path write() throws IOException {
        Path file = dir.resolve("testdb");
        Files.writeString(file, YAML);
        return file;
    }

    // test resolve vars in db section: create YAML containing placeholders and if it is equal to VARS (password and username)
    // check whether placeholder replaced with actual values
    @Test
    void resolvesVariablesInDatabaseSection() throws IOException {
        DbVaultConfig c = ConfigLoader.load(write(), null, VARS);
        assertEquals(DatabaseType.POSTGRES, c.database().type());
        assertEquals("jack", c.database().username());  //DB_USERNAME
        assertEquals("secret", c.database().password()); //DB_PASSWORD
    }

    // test if default (without -s) app use LOCAL storage, we only provide db vars
    @Test
    void usesDefaultStorageAndNotNeedOtherStorageVariables() throws IOException {
        DbVaultConfig c = ConfigLoader.load(write(), null, VARS);
        assertEquals(StorageType.LOCAL, c.storage().type());
        assertEquals("./backups", c.storage().path());
    }

    // test if we provide explicit storage type that it overrides default option (LOCAL)
    @Test
    void storageNameOverridesDefault() throws IOException {
        Map<String, String> vars = Map.of("DB_USERNAME", "jack",
                "DB_PASSWORD", "secret",
                "S3_ACCESS_KEY", "AK",
                "S3_SECRET_KEY", "SK");
        DbVaultConfig c = ConfigLoader.load(write(), "s3", vars);
        assertEquals(StorageType.S3, c.storage().type());
        assertEquals("AK", c.storage().accessKey());
        assertEquals("SK", c.storage().secretKey());
    }

    //Test if S3 storage var miss secret key and return exception requiring it
    @Test
    void chosenStorageWithMissingSecretKeyFails() throws IOException {
        Path file = write();
        DbVaultException e = assertThrows(DbVaultException.class,
                () -> ConfigLoader.load(file, "s3", VARS));
        assertTrue(e.getMessage().contains("S3_ACCESS_KEY"));
    }

    // empty map is no vars, test check it out intended exception in case of missing var
    @Test
    void missingVariables() throws IOException {
        Path file = write();
        DbVaultException e = assertThrows(DbVaultException.class,
                () -> ConfigLoader.load(file, null, Map.of()));
        assertTrue(e.getMessage().contains("Variable not set: DB_USERNAME"));
    }

    // test if user add unknown storage name, exception should be thrown with given name
    @Test
    void unknownStorageTypeFails() throws IOException {
        Path file = write();
        DbVaultException e = assertThrows(DbVaultException.class,
                () -> ConfigLoader.load(file, "nope", VARS));
        assertTrue(e.getMessage().contains("Unknown storage 'nope'"));
    }

    // path to file that doesn't exist, it would fail and return intended exception
    @Test
    void missingFileFails() throws IOException {
        DbVaultException e = assertThrows(DbVaultException.class,
                () -> ConfigLoader.load(dir.resolve("empty.yml"), null, VARS ));
        assertTrue(e.getMessage().contains("Config file does not exist"));
    }
}
