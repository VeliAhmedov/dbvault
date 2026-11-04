package io.github.veliahmedov.dbvault.storage;

import io.github.veliahmedov.dbvault.config.StorageConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import java.nio.file.Path;

//same functionality as AdapterFactory
public final class StorageFactory {

    private StorageFactory() {}

    //check config exists and selected type, create impl of it and return as StorageBackend
    public static StorageBackend create(StorageConfig config) {
        if (config == null || config.type() == null) throw new DbVaultException("Invalid storage config");
        return switch (config.type()){
            case LOCAL -> new LocalStorageBackend(Path.of(config.path() == null ? "./backups" : config.path()));
        };
    }
}
