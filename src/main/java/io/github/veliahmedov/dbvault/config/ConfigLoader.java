package io.github.veliahmedov.dbvault.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//this component read env var, replace placeholder in yaml, return fully populated java configuration objects
public final class ConfigLoader {

    // structure of yml file, Jackson reads, used map because number and name of storage (s3, local) can change in cli to be more flexible
    // RawConfig(database,"local", Map.of("s3", localStorageConfig));, you can configure yourself in cmd, notification for notify
    private record RawConfig(DatabaseConfig database, String defaultStorage, Map<String, StorageConfig> storages, NotificationConfig notification) {}

    // location of .env files and placeholders inside yaml file
    private static final Path DOTENV = Path.of(".env");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    // Jackson YAML module for converting and reading YAML to java object (deserialization)
    // enable enum values without case sensitivity
    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    //private no argument constructor to prevent creating instance of class (utility class)
    private ConfigLoader() {}

    //load configuration from yaml to records as objects
    public static DbVaultConfig load(Path file, String storageName) {
        if (!Files.exists(file)) {
            throw new DbVaultException("Config file does not exist: " + file);
        }
        RawConfig rawConfig;
        try {
            //deserialize, extract info from given YAML file to java object
            rawConfig = MAPPER.readValue(file.toFile(), RawConfig.class);
        }catch (IOException e) {
            throw new DbVaultException("Failed to load config file: " + file, e);
        }
        if (rawConfig.database() == null || rawConfig.database().type() == null) {
            throw new DbVaultException("Config database or database type not found");
        }
        Map<String, String> vars = loadVariables();
        return new DbVaultConfig(
                resolveDatabase(rawConfig.database, vars),
                resolveStorage(pick(rawConfig, storageName), vars),
                resolveNotifications(rawConfig.notification, vars));
    }

    //only chosen storage get ${}, it up to use to write in cli which storage config should dbvault u se
    private static StorageConfig pick(RawConfig raw, String requested) {
        // if storage missing use empty map
        Map<String, StorageConfig> all = raw.storages() == null ? Map.of() : raw.storages();
        // if user write --storage s4, requested is s3, otherwise use defaultStorage
        String name = requested != null ? requested : raw.defaultStorage();
        // if only one configured storage and no default specified, Dbvault will select it
        if (name == null && all.size() == 1) name = all.keySet().iterator().next();
        if (name == null)
            throw new DbVaultException("No storage chosen. Use --storage or set defaultStorage. Available: " + all.keySet());
        // if user specifies non-existent name then throw exception
        StorageConfig s = all.get(name);
        if (s == null)
            throw new DbVaultException("Unknown storage '" + name + "'. Available: " + all.keySet());
        return s;
    }

    // loads and combine .env from readDotEnv and OS env variables into one
    private static Map<String, String> loadVariables() {
        Map<String, String> vars = new HashMap<>(readDotEnv());
        vars.putAll(System.getenv());
        return vars;
    }

    // reads .env file return its contents
    private static Map<String, String> readDotEnv() {
        Map<String, String> map = new HashMap<>();
        if (!Files.isRegularFile(DOTENV)) return map; //if it doesn't exist return empty map for user to fill in
        try {
            for (String line : Files.readAllLines(DOTENV)) {   //read every line from .env file
                line = line.trim();
                //comments and empty lines are ignored
                if (line.isEmpty() || line.startsWith("#")) continue;
                //cut after export to extract env var
                if (line.startsWith("export ")) line = line.substring(7).trim();
                // use place of = to separate keys and values
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // remove surrounding quotes for both " and ' and store it
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            throw new DbVaultException("Cannot read .env file", e);
        }
        return map;
    }

    // if value contains ${VARIABLE}, it replaces it with env var if not contains then simply returns original value
    private static String resolve(String value, Map<String, String> vars) {
        if (value == null) return null;
        return PLACEHOLDER.matcher(value).replaceAll(r -> {
            String v = vars.get(r.group(1)); // for ${DB_PASSWORD} return DB_PASSWORD
            if (v == null) {
                throw new DbVaultException("Variable not set: " + r.group(1)
                        + " (set it in the terminal or in .env)");
            }
            return Matcher.quoteReplacement(v);
        });
    }

    // resolveAll divided into 2 parts for readability and single responsibility reasons
    private static DatabaseConfig resolveDatabase(DatabaseConfig d, Map<String, String> vars) {
        return new DatabaseConfig(d.type(), resolve(d.host(), vars), d.port(), resolve(d.name(), vars),
                resolve(d.username(), vars), resolve(d.password(), vars), resolve(d.container(), vars));
    }
    private static StorageConfig resolveStorage(StorageConfig s, Map<String, String> vars) {
        return new StorageConfig(s.type(), resolve(s.path(), vars),
                resolve(s.endpoint(), vars), resolve(s.bucket(), vars), resolve(s.region(), vars),
                resolve(s.accessKey(), vars), resolve(s.secretKey(), vars));
    }
    //additional resolveNotifications for resolving notify params alongside db and storage
    private static NotificationConfig resolveNotifications(NotificationConfig n, Map<String, String> vars) {
        return n == null ? null : new NotificationConfig(resolve(n.webhook(), vars), resolve(n.notifyOn(), vars));
    }
    // old code used for one storage config back then using
//    // this time apply everything to hole configuration with resolve
//    private static DbVaultConfig resolveAll(DbVaultConfig c, Map<String, String> vars) {
//        // return object with actual values put in from yaml, port and type isn't put because they aren't string
//        DatabaseConfig d = c.database();
//        d = new DatabaseConfig(d.type(), resolve(d.host(), vars), d.port(), resolve(d.name(), vars),
//                resolve(d.username(), vars), resolve(d.password(), vars), resolve(d.container(), vars));
//        // same thing as database but for storage, added S3 env vars
//        StorageConfig s = c.storage();
//        // non env ones (path, endpoint and etc.) return unchanged but env vars (secret and access keys) replaced with env values
//        if (s != null) s = new StorageConfig(s.type(),
//                resolve(s.path(), vars),
//                resolve(s.endpoint(), vars),
//                resolve(s.bucket(), vars),
//                resolve(s.region(), vars),
//                resolve(s.accessKey(), vars),
//                resolve(s.secretKey(), vars));
//        // return all 2 combined in dbVaultConfig
//        return new DbVaultConfig(d, s);
//    }
}
