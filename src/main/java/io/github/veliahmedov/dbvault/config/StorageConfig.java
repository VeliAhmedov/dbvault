package io.github.veliahmedov.dbvault.config;

//hold storage configuration settings
public record StorageConfig(
        StorageType type,             // LOCAL
        String path                   // ./backups (where stored)
) {
}
// Jackson does map these as objects StorageConfig(StorageType.LOCAL, "./backups")
