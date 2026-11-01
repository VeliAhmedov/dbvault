package io.github.veliahmedov.dbvault.cli;

import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "verify",
        mixinStandardHelpOptions = true,
        description = "Verify the integrity of a backup"
)
public class VerifyCommand implements Callable<Integer>{

    @Override
    public Integer call() {
        System.out.println("verify: work in progress");
        return 0;
    }
}
