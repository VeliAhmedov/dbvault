package io.github.veliahmedov.dbvault.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
// utility class that handles saving and reading backup metadata for use
public final class BackupMetadataStore {
    private BackupMetadataStore() {}

    //JSON to java onj, java obj to JSON converter. nicely formatted
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    // if backup is mydb.dump.gz | it become mydb.dump.gz.json
    public static String keyFor(String backup){
        return backup + ".json";
    }

    // save metadata as JSON
    public static void write(StorageBackend storage, BackupMetadata meta) throws IOException {
        try (OutputStream out = storage.openWrite(keyFor(meta.file()))) {
            MAPPER.writeValue(out, meta); //BackupMetadata convert to JSON and write into storage out stream
        }
    }

    //opposite of write, download .json, read its contents and convert back to BackupMetadata. you can access sha256, sizeBytes and db
    public static BackupMetadata read(StorageBackend storage, String backup) throws IOException {
        try (InputStream in = storage.download(keyFor(backup))){
            return MAPPER.readValue(in, BackupMetadata.class);
        }
    }
}
