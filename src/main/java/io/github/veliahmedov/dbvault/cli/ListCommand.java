package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.storage.BackupMetadata;
import io.github.veliahmedov.dbvault.storage.BackupMetadataStore;
import io.github.veliahmedov.dbvault.storage.StorageBackend;
import io.github.veliahmedov.dbvault.storage.StorageFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

// this component shows available backups currently
@Command(name = "list",
        mixinStandardHelpOptions = true,
        description = "list all available backups"
)
public class ListCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    private Path configFilePath;

    @Option(names = {"-s", "--storage"}, description = "Storage name from the config file, default: local")
    private String storageName;

    @Override
    public Integer call() throws IOException {
        //load config and storage
        DbVaultConfig config = ConfigLoader.load(configFilePath, storageName);
        StorageBackend storage = StorageFactory.create(config.storage());

        // print table header for values
        System.out.printf("%-40s %10s  %-19s %s%n", "BACKUP", "SIZE", "CREATED", "SHA-256");
        for (String key : storage.list()) {          // return all stored file names
            if (!key.endsWith(".dump.gz")) continue; // but we only need backup files not metadata files (.json)
            try {
                // read left off JSON files and convert them to BackupMetadata obj
                BackupMetadata m = BackupMetadataStore.read(storage, key);
                // headers,backup filename, size in bytes, creation date, first 12 char of sha-256 hash for peek
                System.out.printf("%-40s %10d  %-19s %s...%n", key, m.sizeBytes(),
                        m.createdAt().substring(0, 19).replace('T', ' '), m.sha256().substring(0, 12));
            } catch (IOException e) {
                // metadata is secondary, backups will still be displayed
                System.out.printf("%-40s %10d  %-19s %s%n", key, storage.size(key), "-", "(no checksum file)");
            }
        }
        return 0;
    }
}
