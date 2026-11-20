package io.github.veliahmedov.dbvault.storage;


import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Unit tests: DbVault generate file name based on db name and timestamp, test if it works as intended
public class BackupNamingTest {
    // general timestamp
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 2, 10, 0, 0);

    // given TIME and name of database return intended name of backup file
    @Test
    void usesDatabaseNameAndTimeStamp(){
        assertEquals("testdb-2026-11-02T10-00-00.dump.gz", BackupNaming.fileName("testdb", TIME));
    }

    // test, if user put name .anything as similar to extension, it doesn't become part of generated name and break it
    @Test
    void stripsFileExtension(){
        assertEquals("test-2026-11-02T10-00-00.dump.gz", BackupNaming.fileName("test.db", TIME));
    }

    // fileName method replace unsafe chars with regex, test if it works as intended
    @Test
    void replaceUnsafeCharacters(){
        assertEquals("test_db_-2026-11-02T10-00-00.dump.gz", BackupNaming.fileName("test db!", TIME));
    }

    // if directory path copied to file name then only add last part, not allow the input to create a path outside the backup directory
    @Test
    void dropsDirectoryPaths(){
        assertEquals("testdb-2026-11-02T10-00-00.dump.gz", BackupNaming.fileName("../../etc/testdb", TIME));
    }

}
