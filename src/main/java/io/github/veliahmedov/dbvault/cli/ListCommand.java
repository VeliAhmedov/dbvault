package io.github.veliahmedov.dbvault.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "list",
        mixinStandardHelpOptions = true,
        description = "list all available backups"
)
public class ListCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("list: work in progress");
        return 0;
    }
}
