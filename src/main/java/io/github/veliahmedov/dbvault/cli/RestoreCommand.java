package io.github.veliahmedov.dbvault.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "restore",
        mixinStandardHelpOptions = true,
        description = "Restore a database from a backup"
)
public class RestoreCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("restore: work in progress");
        return 0;
    }
}
