package io.github.veliahmedov.dbvault.storage;

import io.github.veliahmedov.dbvault.config.StorageConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

// like local impl, its purpose to translate DbVault generic storage operations into S3 API ops
public class S3StorageBackend implements  StorageBackend {

    private final S3Client s3;   // Amazon S3 client
    private final String bucket; // backup destination

    public S3StorageBackend(StorageConfig c){
        // validates that these fields exist
        require(c.endpoint(), "endpoint");
        require(c.bucket(), "bucket");
        require(c.accessKey(), "accessKey");
        require(c.secretKey(), "secretKey");
        this.bucket = c.bucket();
        // build s3 client
        this.s3 = S3Client.builder()
                // connect garage server instead of default AWS S3 servers, it comes from | endpoint: http://localhost:3900
                .endpointOverride(URI.create(c.endpoint()))
                // set signing region
                .region(Region.of(c.region() == null || c.region().isBlank() ? "us-east-1" : c.region()))
                // supply access and secret key for auth
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(c.accessKey(), c.secretKey())))
                // use localhost:3900/bucket/key URL structure  (http://localhost:3900/dbvault-backups/backup.dump.gz)
                .forcePathStyle(true)
                // control (not automate) when SDK calculate req checksum and resp checksum validation
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        try {
            s3.headBucket(b -> b.bucket(bucket));   // check whether bucket (destination) can be accessed or not
        } catch (SdkException e) {
            throw new DbVaultException("Cannot access S3 bucket '" + bucket + "' at "
                    + c.endpoint() + ": " + e.getMessage(), e);
        }
    }
    // validation
    private static void require(String value, String field){
        if (value == null || value.isBlank()){
            throw new DbVaultException("Config storage '" + field + "' is required for type s3");        }
    }
    // custom IOException exception
    private static IOException io(String action, SdkException e) {
        return new IOException("S3 " + action + " failed: " + e.getMessage(), e);
    }

    @Override
    public OutputStream openWrite(String key) throws IOException {
        return new S3OutputStream(s3, bucket, key);
    }

    @Override
    public InputStream download(String key) throws IOException {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        }catch (SdkException e) {
            throw io("download", e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        }catch (SdkException e) {
            throw io("delete", e);
        }
    }

    @Override
    public List<String> list() throws IOException {
        try {
            return s3.listObjectsV2Paginator(b -> b.bucket(bucket)).contents().stream()
                    .map(S3Object::key).sorted().toList();
        } catch (SdkException e){
            throw io("list", e);
        }
    }

    @Override
    public long size(String key) throws IOException {
        try {
            return s3.headObject(b -> b.bucket(bucket).key(key)).contentLength();
        } catch (SdkException e){
            throw io("size", e);
        }
    }
}
