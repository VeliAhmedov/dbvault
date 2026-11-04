package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.db.AdapterFactory;
import io.github.veliahmedov.dbvault.db.DatabaseAdapter;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import io.github.veliahmedov.dbvault.storage.BackupNaming;
import io.github.veliahmedov.dbvault.storage.StorageBackend;
import io.github.veliahmedov.dbvault.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

//child cmd, name means (dbvault backup) where main cmd picks up by name
@Command(name = "backup",
        mixinStandardHelpOptions = true,
        description = "Create a backup of a database"
)                                                       //exp: java -jar dbvault.jar backup
public class BackupCommand implements Callable<Integer> {

    //log factory for logging results, info, warning and errors for all
    private static final Logger log = LoggerFactory.getLogger(BackupCommand.class);

    // @Option Picocli annotation to accept commands named -c and --config (-c is shortened --config)
    // (java -jar dbvault.jar backup -c dbvault.example.yml), recognize c, read YAML, convert to path
    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    // if user don't provide -c, Picocli use dbvault.yml so cmd above is same as (java -jar dbvault.jar backup)
    private Path configFilePath;

    //callable executed because need to return result as CLI command should return code for success (0) and failure (1)
    @Override
    public Integer call() {
        DbVaultConfig config = ConfigLoader.load(configFilePath);  //loads dbvault.yml YAML to Object to it
        DatabaseAdapter adapter = AdapterFactory.create(config.database());  // with general info it creates specialized adapter for given DB
        StorageBackend storage = StorageFactory.create(config.storage());    // with general info it creates specialized storage for given DB backup

        //test before if DB is accessible via psql
        adapter.testConnection();
        //generate backup filename, log it
        String backupFilename = BackupNaming.fileName(config.database().name(), LocalDateTime.now());
        log.info("Backup Started: using {} adapter for database '{}'", config.database().type(), config.database().name());
        Instant start = Instant.now();

        //actual backup and compression ops
        //pg_dump (Produces PostgreSQL data) ->  p.getInputStream() (Java reads stdout) ->  out (GZIPOutputStream compresses it) ->  Backup file
        // ask storage backend to open destination file for write, return outStream, wrap it with GZIP and write backup to given output stream
        try (OutputStream out = new GZIPOutputStream(storage.openWrite(backupFilename))) {
            adapter.backup(out);
        } catch (IOException | RuntimeException e) {
            try {
                // if backup fails, delete incomplete file to prevent incomplete posing as success and to retry
                storage.delete(backupFilename);
            } catch (IOException ex) {
                log.warn("Could not remove partial file {}", backupFilename);
            }
            // if it was our own ex (inside runtimeexception) then don't wrap it up, throw itself
            if (e instanceof DbVaultException dbv) throw dbv;
            throw new DbVaultException("Backup failed: " + e.getMessage(), e);
        }
        try {
            log.info("Backup finished: {} | {} bytes | {} ms | SUCCESS",
                    backupFilename, storage.size(backupFilename),
                    Duration.between(start, Instant.now()).toMillis());
        } catch (IOException ioe) {
            throw new DbVaultException("Backup written but size check failed", ioe);
        }
        return 0;
    }
}
// overall flow:

// User run dbvault backup
//          ↓
//   BackupCommand.call()
//          ↓
// ConfigLoader (loads configuration),
// AdapterFactory (create DB adapter),
// StorageFactory (create storage backend),
// BackupNaming (generate backup file name)
//          ↓
// DatabaseAdapter.backup(out)
//          ↓
//    GZIPOutputStream
//          ↓
//     StorageBackend

