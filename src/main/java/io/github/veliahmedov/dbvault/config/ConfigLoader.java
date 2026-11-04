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
        return resolveAll(config, loadVariables());
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

    // Replaces ${NAME} and other placeholder in a value
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

    // this time apply everything to hole configuration with resolve
    private static DbVaultConfig resolveAll(DbVaultConfig c, Map<String, String> vars) {
        // return object with actual values put in from yaml, port and type isn't put because they aren't string
        DatabaseConfig d = c.database();
        d = new DatabaseConfig(d.type(), resolve(d.host(), vars), d.port(), resolve(d.name(), vars),
                resolve(d.username(), vars), resolve(d.password(), vars), resolve(d.container(), vars));
        // same thing as database but for storage
        StorageConfig s = c.storage();
        if (s != null) s = new StorageConfig(s.type(), resolve(s.path(), vars));
        // return all 2 combined in dbVaultConfig
        return new DbVaultConfig(d, s);
    }
}
