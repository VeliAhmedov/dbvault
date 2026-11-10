package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import io.github.veliahmedov.dbvault.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

// This component checks if backup is CORRUPTED or INTACT
@Command(name = "verify",
        mixinStandardHelpOptions = true,
        description = "Verify the integrity of a backup"
)
public class VerifyCommand implements Callable<Integer>{
    private static final Logger LOG = LoggerFactory.getLogger(VerifyCommand.class);

    @Parameters(index = "0", paramLabel = "<backup>", description = "Backup file name")
    private String backup;

    // take values from db.vault with given -c
    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    private Path configFilePath;

    @Override
    public Integer call() throws IOException {
        //loading config and storage
        DbVaultConfig config = ConfigLoader.load(configFilePath);
        StorageBackend storage = StorageFactory.create(config.storage());
        //check whether backup exists and metadata exists (testdb.dump.gz.json)
        List<String> files = storage.list();
        if (!files.contains(backup)) {
            throw new DbVaultException("Backup not found in storage: " + backup);
        }
        if (!files.contains(BackupMetadataStore.keyFor(backup))) {
            throw new DbVaultException("No checksum file for " + backup + " (cannot verify)");
        }
        // Read metadata and compare sha256 hash sizes (not string), any modification can affect even of KB difference
        BackupMetadata meta = BackupMetadataStore.read(storage, backup);
        if (storage.size(backup) != meta.sizeBytes()){
            throw new DbVaultException("CORRUPTED: size differs from recorded size: " + backup);
        }
        //download backup, calculate and compare actual checksum SHA-256 hash strings, main function to check corruption
        String actual;  // actual = hash calculated now, meta.sha256 = when backup created
        try (InputStream in = storage.download(backup)) {
            actual = Checksums.sha256Of(in);             // sha256Of use DigestInputStream to calculate while reading
        }
        if (!actual.equals(meta.sha256())){
            throw new DbVaultException("CORRUPTED: checksum mismatch: " + backup);
        }
        LOG.info("Verified OK: {} | sha256: {}...", backup, meta.sha256());
        return 0;
    }
}
