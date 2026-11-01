package io.github.veliahmedov.dbvault.config;

//combine database and storage config objects into one root config object
public record DbVaultConfig(
        DatabaseConfig database,
        StorageConfig storage
) {
}
