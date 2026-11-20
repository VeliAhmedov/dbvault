package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// check if said errors throw intended exception for Postgres errors list, must provide useful reports on errors to fix it
public class PostgresErrorsTest {
    // credentials for potgress/docker
    private final PostgresErrors errors = new PostgresErrors("jack", "testdb", "mybox");

    // translate low level postgres/docker errors into readable messages via class's ready method
    private String translated(String raw) {
        return errors.translate(new DbVaultException(raw)).getMessage();
    }

    // in case of auth failure
    @Test
    void authFailure() throws DbVaultException {
        assertTrue(translated("psql failed (exit 2): FATAL:  password authentication failed for user \"jack\"")
                .contains("Authentication failed for user 'jack'"));
    }
    // test in case of missing container
    @Test
    void missingContainer() {
        assertTrue(translated("docker failed (exit 1): Error response from daemon: No such container: mybox")
                .contains("Docker container 'mybox' not found"));
    }

    //test in case of stopped container (happens a lot)
    @Test
    void stoppedContainer() {
        assertTrue(translated("docker failed (exit 1): Error response from daemon: container abc is not running")
                .contains("is not running"));
    }

    // test in case of missing database
    @Test
    void missingDatabase() {
        assertTrue(translated("psql failed (exit 2): FATAL:  database \"testdb\" does not exist")
                .contains("Database 'testdb' does not exist"));
    }

    // test in case of if db existed but misses needed parts like tables, column etc., check translator
    @Test
    void otherMissingObjectsAreNotReportedAsMissingDatabase() {
        DbVaultException original = new DbVaultException("psql failed: relation \"x\" does not exist");
        assertSame(original, errors.translate(original));
    }

    // unknown error passes, original error instead should be return to not create confusion
    @Test
    void unknownErrorPassesThroughAndKnownKeepsCause() {
        DbVaultException unknown = new DbVaultException("something unexpected");
        assertSame(unknown, errors.translate(unknown));

        DbVaultException known = new DbVaultException("Connection refused");
        assertSame(known, errors.translate(known).getCause());
    }
}
