package io.github.veliahmedov.dbvault.storage;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests: test features of local storage backend, including metadata
public class LocalStorageBackendTest {
    // temporary test environment
    @TempDir
    Path dir;

    // test all in all ops, write, read, list, size and delete backup
    @Test
    void writeReadListSizeDelete() throws IOException {
        //local storage backup directory
        StorageBackend storage = new LocalStorageBackend(dir);
        // then open file and write "hello" into backup file name with name
        try(OutputStream out = storage.openWrite("b.dump.gz")){
            out.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        // check if list return backup file name and size return 5 bytes
        assertEquals(List.of("b.dump.gz"), storage.list());
        assertEquals(5L, storage.size("b.dump.gz"));
        // then for restore download and read file if its contents still "hello"
        try (InputStream in = storage.download("b.dump.gz")) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // then delete and check if it is deleted
        storage.delete("b.dump.gz");
        assertTrue(storage.list().isEmpty());
    }

    // check if our exception against path traversal works or not
    // someone might write ../name to write outside of storage directory
    @Test
    void rejectsPathTraversal() {
        StorageBackend storage = new LocalStorageBackend(dir);
        assertThrows(DbVaultException.class, () -> storage.openWrite("../part"));
    }

    // check if metadata is same as values we put in (round-trip test)
    @Test
    void metadataRoundTrip() throws IOException {
        // create storage and metadata for that storage then save it through metadata store
        StorageBackend storage = new LocalStorageBackend(dir);
        BackupMetadata meta = new BackupMetadata("x.dump.gz", "testdb", "2026-11-14T10:00:00Z", 123L, "abc");
        BackupMetadataStore.write(storage, meta);
        // check 1. metadata file created, 2. same data values as when created, no initial corruption
        assertTrue(storage.list().contains("x.dump.gz.json"));
        assertEquals(meta, BackupMetadataStore.read(storage, "x.dump.gz"));
    }

}
