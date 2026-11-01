package io.github.veliahmedov.dbvault.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//this component  read yaml config files, convert to java object, validate it and apply env var password
public final class ConfigLoader {

    private static final String PASSWORD_ENV = "DBVAULT_PASSWORD";

    // Jackson YAML module for converting and reading YAML to java object (deserialization)
    // enable enum values without case sensitivity
    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    //private no argument constructor to prevent creating instance of class (utility class)
    private ConfigLoader() {}

    //load configuration from yaml to records as objects
    public static DbVaultConfig load(Path file) {
        if (!Files.exists(file)) {
            throw new DbVaultException("Config file does not exist: " + file);
        }
        DbVaultConfig config;
        try {
            //deserialize, extract info from given YAML file to java object
            config = MAPPER.readValue(file.toFile(), DbVaultConfig.class);
        }catch (IOException e) {
            throw new DbVaultException("Failed to load config file: " + file, e);
        }
        if (config.database() == null || config.database().type() == null) {
            throw new DbVaultException("Config database or database type not found");
        }
        return loadFromEnv(config);
    }

    // password is env variable, we can't put value directly your command line
    private static  DbVaultConfig loadFromEnv(DbVaultConfig config) {
        //read OS env var to prevent commit of passwords to yaml file
        String password = System.getenv(PASSWORD_ENV);
        if (password == null || password.isBlank()) {
            return config;
        }
        return new DbVaultConfig(config.database().withPassword(password), config.storage());
    }
}
