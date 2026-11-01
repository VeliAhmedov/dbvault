package io.github.veliahmedov.dbvault.config;

// holds database connection command settings, examples:
public record DatabaseConfig(
        DatabaseType type,            // POSTGRES
        String host,                  // localhost
        Integer port,                 // 5433
        String name,                  // testdb (db name)
        String username,              // dbvault
        String password
) {
    //only things that can be changed is password other remain same (records are immutable)
    public  DatabaseConfig withPassword(String newPassword) {
        return new DatabaseConfig(type, host, port, name, username, newPassword);
    }
}
// Jackson does map these, only need change is password which is withPassword method
