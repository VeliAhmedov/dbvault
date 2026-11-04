package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import java.util.List;

// Translate raw psql/pg_dump/docker errors to messages
public class PostgresErrors {
    //hold keyword phrase for search and respectively message for each
    private record Rule(List<String> keywords, String message) {}
    //error translation rules
    private final List<Rule> rules;

    public PostgresErrors(String username, String database, String container) {
        rules = List.of(
                new Rule(List.of("password authentication failed", "no password supplied"),
                        "Authentication failed for user '" + username + "'. Check the credentials in your config or .env."),
                new Rule(List.of("No such container"),
                        "Docker container '" + container + "' not found. Check 'docker ps'."),
                new Rule(List.of("is not running"),
                        "Docker container '" + container + "' is not running."),
                new Rule(List.of("Connection refused", "could not connect"),
                        "Cannot reach Postgres. Is the server running?"),
                new Rule(List.of("database \"" + database + "\" does not exist"),
                        "Database '" + database + "' does not exist."),
                new Rule(List.of("server version mismatch"),
                        "pg_dump is older than the server. Install client tools 18+ or use 'container:'.")
        );
    }

    // examine error to check if it matches
    DbVaultException translate(DbVaultException ex){
        String message = ex.getMessage() == null ? "" : ex.getMessage();  //original message
        return rules.stream()
                .filter(r -> r.keywords.stream().anyMatch(message:: contains))
                .findFirst()
                .map(r -> new DbVaultException(r.message(), ex))      // this is original error
                .orElse(ex);                                                // after all fails, pass it as unknown error
    }
}
