package io.github.veliahmedov.dbvault.storage;

import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class LocalStorageBackend implements StorageBackend {
    private final Path dir;

    // create backup directory when instance created ./backups -> \backups
    public LocalStorageBackend(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new DbVaultException("Could not create backup folder: " + dir, e);
        }
    }

    //security check against Path traversal and outside access of files for every main methods
    private Path resolve(String key) throws IOException {
        Path p = dir.resolve(key).normalize();
        if (!p.startsWith(dir)) {
            throw new DbVaultException("Invalid backup name: " + key);
        }
        return p;
    }

    @Override
    public OutputStream openWrite(String key) throws IOException {
        return Files.newOutputStream(resolve(key));  //open file for writing if exists it is truncated if not created and written
    }

    @Override
    public InputStream download(String key) throws IOException {
        return Files.newInputStream(resolve(key));  //return stream for reading it
    }

    @Override
    public void delete(String key) throws IOException {
        Files.delete(resolve(key));
    }

    @Override
    public List<String> list(String key) throws IOException {   // get list of files in backup
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted().toList();
        }
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }
}
