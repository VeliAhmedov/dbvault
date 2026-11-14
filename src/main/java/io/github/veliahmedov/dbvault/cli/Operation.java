package io.github.veliahmedov.dbvault.cli;

import io.github.veliahmedov.dbvault.exception.ExitCode;
import io.github.veliahmedov.dbvault.notification.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

// main utility class logs wrapper for:
// 1. log ops start
// 2. measure exe time
// 3. log success or failure
// 4. send notification
// 5. allow exception to reach central handler (dbvaultCommand)
// used for sending notification webhook
public class Operation {
    private static final Logger LOG = LoggerFactory.getLogger(Operation.class.getName());

    private Operation() {}

    @FunctionalInterface
    interface Body{
        //() -> backup(config) fir returning string result of command in cli to be seen
        // it return string of named ops (backup, restore)
        String run() throws IOException;
    }

    // name = ops name (backup, restore), subject = what being processed (testdb),
    // notifier = notification impl (WebhookNotifier)
    // body = ops that will be executed
    static int run(String name, String subject, Notifier notifier, Body body) throws IOException {
        //ops name and time it started and logged
        String label = name.toLowerCase();
        Instant instant = Instant.now();
        LOG.info("{} started: {}", label, subject);
        try {
            // execute actual operation (backup(config), restore(config, storage) or verify(config))
            String detail =  body.run();
            //duration between ops started and finished, alongside if not empty add detail from ops to log as well
            long ms = Duration.between(instant, Instant.now()).toMillis();
            String suffix = detail.isEmpty() ? "" : " | " + detail;
            LOG.info("{} finished: {} | duration={} ms | status=SUCCESS {}", label, subject, ms, suffix);
            //send notification about ops and get exit code
            notifier.send(true, "[OK] dbvault " + name + " " + subject + suffix);
            return ExitCode.SUCCESS.getCode();
        }catch (IOException | RuntimeException e) {
            // exception logged, send notification about it and exception rethrown
            // as why rethrown because clean exception is better instead of exit code as DbVaultCommand does it
            long ms = Duration.between(instant, Instant.now()).toMillis();
            LOG.error("{} failed: {} | duration={}ms | status=FAILED | error={}", label, subject, ms, e.getMessage());
            notifier.send(false, "[FAILED] dbvault " + name + " " + subject + ": " + e.getMessage());
            throw e;
        }
    }
}
// Operation and DbVaultCommand is closely connected classes but separation needed as former handles log and notification
// and letter handle exception adn xit codes

// rethrow exception = to take exception do operations like send notification and log then throw again called rethrowing
