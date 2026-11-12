package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.db.AdapterFactory;
import io.github.veliahmedov.dbvault.db.DatabaseAdapter;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import io.github.veliahmedov.dbvault.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
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
    private static final Logger LOG = LoggerFactory.getLogger(BackupCommand.class);

    // @Option Picocli annotation to accept commands named -c and --config (-c is shortened --config)
    // (java -jar dbvault.jar backup -c dbvault.example.yml), recognize c, read YAML, convert to path
    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    // if user don't provide -c, Picocli use dbvault.yml so cmd above is same as (java -jar dbvault.jar backup)
    private Path configFilePath;

    // instead of hardcoding storage type like s3 or local, write just: dbvault backup -s s3 | dbvault backup --storage s3
    @Option(names = {"-s", "--storage"}, description = "Storage name from the config file, default: local")
    private String storageName;

    //callable executed because need to return result as CLI command should return code for success (0) and failure (1)
    @Override
    public Integer call() {
        DbVaultConfig config = ConfigLoader.load(configFilePath, storageName);  //loads dbvault.yml YAML to Object to it
        DatabaseAdapter adapter = AdapterFactory.create(config.database());  // with general info it creates specialized adapter for given DB
        StorageBackend storage = StorageFactory.create(config.storage());    // with general info it creates specialized storage for given DB backup

        //test before if DB is accessible via psql
        adapter.testConnection();
        //generate backup filename, log it
        String backupFilename = BackupNaming.fileName(config.database().name(), LocalDateTime.now());
        LOG.info("Backup Started: using {} adapter for database '{}'", config.database().type(), config.database().name());
        Instant start = Instant.now();

        //hashing engine for SHA-256 calculator
        MessageDigest digest = Checksums.sha256();
        //actual backup, compression ops and hash checksum
        //pg_dump (Produces PostgreSQL data) ->  p.getInputStream() ->  out (GZIPOutputStream compresses it) -> digest (hash compressed bytes) ->  Backup file
        try (OutputStream out = new GZIPOutputStream(
                new DigestOutputStream(storage.openWrite(backupFilename), digest))) {   //calculate hash while writing data
            adapter.backup(out);
        } catch (IOException | RuntimeException e) {
            try {
                // if backup fails, delete incomplete file to prevent incomplete posing as success and to retry
                storage.delete(backupFilename);
            } catch (IOException ex) {
                LOG.warn("Could not remove partial file {}", backupFilename);
            }
            // if it was our own ex (inside runtime exception) then don't wrap it up, throw itself
            if (e instanceof DbVaultException dbv) throw dbv;
            throw new DbVaultException("Backup failed: " + e.getMessage(), e);
        }
        try {
            //save checksum metadata, you will use it for restore to compare in case of corruption
            long size = storage.size(backupFilename);
            String sha = Checksums.hex(digest);
            BackupMetadataStore.write(storage,
                    new BackupMetadata(backupFilename, config.database().name(), Instant.now().toString(), size, sha));

            LOG.info("Backup finished: {} | {} bytes | {} ms | sha256 {}... | SUCCESS",
                    backupFilename, size, Duration.between(start, Instant.now()).toMillis(), sha.substring(0, 12));
        } catch (IOException ioe) {
            throw new DbVaultException("Backup written but checksum file failed", ioe);
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

