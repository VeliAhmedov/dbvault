package io.github.veliahmedov.dbvault.storage;

// information about a backup
public record BackupMetadata(
        String file,               // testdb.dump.gz
        String database,           // testdb
        String createdAt,          // 2026-11-07T20:00:00Z
        long sizeBytes,            // 1231312 (backup size in bytes)
        String sha256              // 64 char hash (backup checksum)
) {
}
