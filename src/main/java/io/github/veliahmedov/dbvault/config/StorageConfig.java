package io.github.veliahmedov.dbvault.config;

//hold storage configuration settings
public record StorageConfig(
        StorageType type,              // LOCAL or S3
        String path,                   // ./backups (where stored)
        String endpoint,               // S3 server address (http://localhost:3900)
        String bucket,                 // Container for backup objects (dbvault-backups)
        String region,                 // Region used for AWS request signing (garage, seaweedFS, MinIO)
        String accessKey,              // S3 access identifier (Garage Key ID)
        String secretKey               // secret used to auth requests (Garage Secret Key)
) {
}
// Jackson does map these as objects StorageConfig(StorageType.LOCAL, "./backups")
// Bucket is storage container inside S3 service you use, mine is Garage, DbVault uploads its backups files into that bucket
// Access and Secret key respectively allow DbVault to auth with Garage
