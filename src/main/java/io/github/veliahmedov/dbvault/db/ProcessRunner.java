package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// this utility class let us execute command lines of databases from JAVA
final class ProcessRunner {
    private ProcessRunner() {
    }

    //Runs command, streams stdout into out, throws stderr text on non-zero exit
    // exp: commands - external cmd (pg_dump -U dbvault), env (PGPASSWORD = dbvault), out - where cmd output goes for processing(GZIP)
    static void streamTo(List<String> commands, Map<String, String> env, OutputStream out) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(commands);  //List<String> command = List.of("pg_dump","-U","dbvault","-d","testdb"); -> pg_dump -U dbvault -d testdb
        pb.environment().putAll(env);                      //alongside commands put needed env variables too for exe,
        Process p;                                         // process used for exe native OS process to manage and interact with running app
        try {
            p = pb.start();     //launch program, return process object that represent running program, with this java can communicate with external program
        } catch (IOException e) {
            throw new DbVaultException("Can't run " + commands.getFirst(), e);  // getFirst return exe name such as pg_dumb of PostgreSQL for backup
        }
        // stderr - standard error (read error messages FROM process) -> p.getErrorStream()
        // stdout - standard output stream (write data TO process) -> p.getOutputStream()
        // stdin - standard input (read output FROM process) -> p.getInputStream()

        //in-memory output stream for capturing error message, stderr collects these bytes for use
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        // we need to read both stdout and stderr, which is why additional virtual thread is used for processing error
        Thread errorReader = Thread.startVirtualThread(() -> {
            try {
                p.getErrorStream().transferTo(stderr);   //transfer error message to stderr
            } catch (IOException e) {
            }
        });

        try {
            //while error is processed, this read process's output and writes to outputStream
            try(InputStream in = p.getInputStream()) {
                in.transferTo(out);
            }
            int code = p.waitFor();   // wait until process finish and return ext code
            errorReader.join();       // wait until virtual thread reading finishes
            if (code != 0) {          // 0 success and 1 failure, if 1 with stderr message we throw message
                throw new DbVaultException(commands.getFirst() + " failed (exit " + code + "): "
                        + stderr.toString(StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException e) {      // current java thread interrupted while waiting waitFor and join
            Thread.currentThread().interrupt(); // catching ex, clear interrupt status, we restore beforehand to make it known
            throw new DbVaultException("Interrupted while running " + commands.getFirst(), e);
        } finally {
            if (p.isAlive()) p.destroyForcibly();
        }
    }
    // convenience method for when we want return stdout as text, used for SQL results and connection checks, small ops
    static String capture(List<String> command, Map<String, String> env) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();   //in-memory  buffer
        try {
            streamTo(command, env, buffer);                           // exe and collect stdout
        }catch (IOException e) {
            throw new DbVaultException("Failed to run " + command.getFirst(), e);
        }
        return buffer.toString(StandardCharsets.UTF_8);               // convert byte to string and return it
    }
}


