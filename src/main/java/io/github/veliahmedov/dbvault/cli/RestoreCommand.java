package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.db.AdapterFactory;
import io.github.veliahmedov.dbvault.db.DatabaseAdapter;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import io.github.veliahmedov.dbvault.exception.ExitCode;
import io.github.veliahmedov.dbvault.notification.Notifier;
import io.github.veliahmedov.dbvault.storage.StorageBackend;
import io.github.veliahmedov.dbvault.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

@Command(name = "restore",
        mixinStandardHelpOptions = true,
        description = "Restore a database from a backup"
)
public class RestoreCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreCommand.class);

    // Picocli positional argument, index 0 means first  value after restore -> dbvault restore testdb.dump.gz assign to private String backup;
    @Parameters(index = "0", paramLabel = "<backup>", description = "Backup file name ( testdb-2026-11-04T23-32-46.dump.gz)")
    private String backup;

    // take values from db.vault with given -c
    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    private Path configFilePath;

    // instead of hardcoding storage type like s3 or local, write just: dbvault backup -s s3 | dbvault backup --storage s3
    @Option(names = {"-s", "--storage"}, description = "Storage name from the config file, default: local")
    private String storageName;

    // allow selective restoration split by , like --table users, accounts
    @Option(names = "--table", split = ",", description = "Restore only this table (repeatable or comma-separated)")
    private List<String> tables = new ArrayList<>();

    // with --yes skip confirmation
    //TODO: yes bypass protection of overwriting, make sure it is safe when using against prod DBs
    @Option(names = "--yes", description = "Skip the confirmation prompt")
    private boolean yes;

    @Override
    public Integer call() throws  IOException {
        // load YAML config, create appropriate storage backend
        DbVaultConfig config = ConfigLoader.load(configFilePath, storageName);
        StorageBackend storage = StorageFactory.create(config.storage());
        Notifier notifier = Notifier.from(config.notification());

        // validation for backup file before Operation.run()
        String db = config.database().name();
        if (!backup.endsWith(".dump.gz"))
            throw new DbVaultException("Unsupported backup file (expected *.dump.gz): " + backup);
        if (!storage.list().contains(backup))
            throw new DbVaultException("Backup not found in storage: " + backup);

        // if not --yes and not confirmed then canceled, to prevent unnecessary notification, just log it
        if (!yes && !confirm(db)){
            LOG.info("Restore cancelled");
            return ExitCode.RESTORE_CANCELLED.getCode();
        }
        // actual restoration call
        return Operation.run("restore", backup + " -> " + db, notifier, () -> restore(config, storage));
    }
    // actual restoration  method, moved here from call()
    private String restore(DbVaultConfig config, StorageBackend storage) throws IOException {
        DatabaseAdapter adapter = AdapterFactory.create(config.database());
        // check if it is reachable
        adapter.testConnection();
        //actual restoration
        //download backup from storage, decompress it with GZIPInputStream, pass it to DB adapter then PostgreSQL send to pg-restore
        try (InputStream in = new GZIPInputStream(storage.download(backup))) {
            adapter.restore(in, tables);
        }
        return tables.isEmpty() ? "full restore" : "tables " + tables;
    }

    // method ask user to confirm before overwriting data
    private boolean confirm(String db) throws IOException {
        String what = tables.isEmpty()
                ? "ALL data in database '" + db + "'"
                : "table(s) " + tables + " in database '" + db + "'";
        System.err.println("WARNING: this will overwrite " + what + ".");
        System.err.print("Type the database name to continue: ");
        System.err.flush();
        String answer = new BufferedReader(new InputStreamReader(System.in)).readLine();
        return answer != null && answer.trim().equals(db);
    }
}
/*
 overall flow:

 User runs: dbvault restore test.dump.gz --table users --yes -s storage
          ↓
   RestoreCommand.call()
          ↓
 ConfigLoader, Notifier.from, StorageFactory
          ↓
 Validate: file ends with .dump.gz and exists in storage
          ↓
 Confirm by typing the database name (skipped with --yes)
   user cancel -> exit code 4, no notification
          ↓
 Operation.run (logs start/finish/duration/status, sends notification, sets exit code)
          ↓
   RestoreCommand.restore()
          ↓
 AdapterFactory, adapter.testConnection()
          ↓
 StorageBackend.download(backup)
          ↓
 GZIPInputStream (decompresses)
          ↓
 DatabaseAdapter.restore(in, tables)
          ↓
   full restore:      ProcessRunner.streamFrom -> pg_restore (one transaction)
   selective restore: temp file -> pg_restore -l -> RestoreList.filter -> pg_restore -L
          ↓
   PostgreSQL Database
 */

