package io.github.veliahmedov.dbvault.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

//child cmd, name means (dbvault backup) where main cmd picks up by name
@Command(name = "backup",
        mixinStandardHelpOptions = true,
        description = "Create a backup of a database"
)
public class BackupCommand implements Callable<Integer> {

    //callable executed because need to return result for cmd's exit status
    @Override
    public Integer call() {
        System.out.println("backup: work in progress");
        return 0;
    }
}
