package io.github.veliahmedov.dbvault.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

// how DbVault interact with backup storage, similar to databaseAdapter but handles backup storage ops
public interface StorageBackend {
    //save backup data (key is identifier for gz local compressor or S3 object key)
    void upload(String key, InputStream inputStream) throws IOException;
    //retrieve backup data
    InputStream download(String key) throws IOException;
    //delete backup
    void delete(String key) throws IOException;
    //list stored backups
    List<String> list(String key) throws IOException;
}
//TODO: have both local and S3 implement
