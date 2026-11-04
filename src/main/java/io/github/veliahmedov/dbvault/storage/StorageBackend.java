package io.github.veliahmedov.dbvault.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

// how DbVault interact with backup storage, similar to databaseAdapter but handles backup storage ops
public interface StorageBackend {
    //open destination to write backup (key is identifier for gz local compressor or S3 object key)
    OutputStream openWrite(String key) throws IOException;
    //open existing backup for reading backup data
    InputStream download(String key) throws IOException;
    //delete backup
    void delete(String key) throws IOException;
    //list stored backups
    List<String> list(String key) throws IOException;
    // backup size in bytes
    long size(String key) throws IOException;
}
//TODO: have both local and S3 implement

//original push model upload (key, InputStream) is replaced by pull model for use of direct stream through GZIP
// old api wanted there need to provide inputStream and additional buffer, now it isn't needed