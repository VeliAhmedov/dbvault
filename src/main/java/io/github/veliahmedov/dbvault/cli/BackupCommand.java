package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.config.ConfigLoader;
import io.github.veliahmedov.dbvault.config.DbVaultConfig;
import io.github.veliahmedov.dbvault.db.AdapterFactory;
import io.github.veliahmedov.dbvault.db.DatabaseAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.concurrent.Callable;

//child cmd, name means (dbvault backup) where main cmd picks up by name
@Command(name = "backup",
        mixinStandardHelpOptions = true,
        description = "Create a backup of a database"
)
public class BackupCommand implements Callable<Integer> {

    //log factory for logging results, info, warning and errors
    private static final Logger log = LoggerFactory.getLogger(BackupCommand.class);

    // @Option picocli annotation to accept commands named -c and --config (-c is shorten --config)
    // (java -jar dbvault.jar backup -c dbvault.example.yml), recognize c, read yaml, convert to path
    @Option(names = {"-c", "--config"}, defaultValue = "dbvault.yml", description = "Path to config file")
    private Path configFilePath;

    //callable executed because need to return result for cmd's exit status
    @Override
    public Integer call() {
        DbVaultConfig config = ConfigLoader.load(configFilePath);  //loads dbvault.yml YAML to Object to it
        DatabaseAdapter adapter = AdapterFactory.create(config.database());  // with general info it creates specialized adapter for given DB (factory examine type)
        log.info("Using {} adapter for database '{}'",
                config.database().type(), config.database().name());
        return 0;
    }
}
