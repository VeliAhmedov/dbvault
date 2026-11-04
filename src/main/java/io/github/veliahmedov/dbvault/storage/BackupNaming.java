package io.github.veliahmedov.dbvault.storage;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// This utility class creates backups file names as for each not be confused with each other or any danger of same name
public class BackupNaming {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private BackupNaming() {}

    public static String fileName(String dbName, LocalDateTime time) {
        //extract filename, remove last extension, replace non-safe chars with underscores, then format it according o formatter
        String base = Path.of(dbName).getFileName().toString().replaceAll("\\.[^.]+$", "");
        base = base.replaceAll("[^A-Za-z0-9_-]", "_");
        return base + "-" + formatter.format(time) + ".sql.gz";
    }
}
// example: testdb-2026-11-02T23-25-10.sql.gz