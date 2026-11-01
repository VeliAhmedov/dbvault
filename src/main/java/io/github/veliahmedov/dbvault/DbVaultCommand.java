package io.github.veliahmedov.dbvault;

import io.github.veliahmedov.dbvault.cli.BackupCommand;
import io.github.veliahmedov.dbvault.cli.ListCommand;
import io.github.veliahmedov.dbvault.cli.RestoreCommand;
import io.github.veliahmedov.dbvault.cli.VerifyCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

//@command mean picocli java class represent command-line command, alongside provide metadata
@Command(
        name = "dbvault",                              //command name to execute
        mixinStandardHelpOptions = true,               //adds --help and --version
        version = "dbvault 0.1.0",                     //version displayed with --version (java -jar dbvault.jar --version)
        description = "Back up and restore databases", // description of command in --help
        subcommands = {                                // register child command belonging to this command
                BackupCommand.class,
                RestoreCommand.class,
                VerifyCommand.class,
                ListCommand.class
        }
)
public class DbVaultCommand implements Runnable {
    static void main(String[] args) {

        CommandLine cli = new CommandLine(new DbVaultCommand());

        //picocli handling exceptions (Error: Config file not found), ex = exception, cmd = command to execute, fullParseResult = info about cmd arguments
        cli.setExecutionExceptionHandler((ex, cmd, fullParseResult) -> {
            cmd.getErr().println("Error: "+ ex.getMessage());
            return 1;
        });
        //commandLine create command line processor, read @command, discover registers, execute args (commands)
        //exp: (java -jar dbvault.jar backup), identify backup via name and execute backup then 0-success, non-zero terminate
        System.exit(cli.execute(args));
    }
    //uses runnable because no need for result as its purpose is display usage info
    //Picocli call run when root cmd is executed without child or help/version
    @Override
    public void run() {
        CommandLine.usage(this, System.out); // picocli utility method that generate cmd usage info of this (main class)
    }
}
