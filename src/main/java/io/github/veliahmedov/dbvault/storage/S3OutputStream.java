package io.github.veliahmedov.dbvault.storage;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// this component class is responsible:
// to upload backup data to Garage as a stream (instead of loading backup to memory as local)
//backup flow is: pg_dump -> GZIPOutputStream -> StorageBackend
// with S3: pg_dump -> GZIPOutputStream -> S3OutputStream -> Garage
public class S3OutputStream extends OutputStream {
    private static final Logger log = LoggerFactory.getLogger(S3OutputStream.class);
    // each parts uploaded is 8 MB (each piece to upload is 8 MB)
    private static final int PART_SIZE = 8 * 1024 * 1024;
    // s3 = AWS SDK client used to commune with Garage via interacting Amazon S3
    private final S3Client s3;
    // bucket: destination bucket (dbvault-backups) | key: backup file name (test-2026-11-10.dump.gz)
    private final String bucket;
    private final String key;
    // temp memory to hold 8 MB upload part
    private final byte[] buffer = new byte[PART_SIZE];
    // to keep info about successfully uploaded parts, Garage need when multipart upload is done
    private final List<CompletedPart> parts = new ArrayList<>();
    // how many bytes currently inside buffer
    private int filled;
    // to identify multipart upload session
    private String uploadId;
    // closed: prevent write after stream closed | failed : indicate upload failed
    private boolean closed;
    private boolean failed;

    // main components about S3 received
    public S3OutputStream(S3Client s3, String bucket, String key) {
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
    }

    // write 1 byte, delegates to main write method, used for to only need one impl of bugger logic
    @Override
    public void write(int oneByte) throws IOException {
        write(new byte[] { (byte) oneByte }, 0, 1);
    }
    // main buffering method: data is bytes, off is start position and len is number of bytes to write
    // copy data into 8 MB buffer
    @Override
    public void write(byte[] data, int off, int len) throws IOException {
        if (closed || failed) throw new IOException("S3 Stream is closed or failed");
        // load (write to) the buffer untill filled
        while (len > 0) {                                   // run until len (bytes of data) is finished
            int n = Math.min(len, buffer.length - filled);  // amount of data (len) or remaining empty space in buffer (buffer.length - filled) to prevent overflow
            System.arraycopy(data, off, buffer, filled, n); // copy (n) bytes from (data), starting from (off) and paste them into (buffer), where left off (filled)
            filled += n;                                    // filled increased by amount of send to buffer
            off += n;                                       // position changed by amount of send to buffer
            len -= n;                                       // amount is deducted from data length
            // if 8 MB filled upload part to garage to empty buffer
            if (filled == buffer.length) uploadPart();
        }
    }

    // send one part to Garage
    private void uploadPart() throws IOException {
        try {
            // if it is first part, it creates multipart to split files into smaller ones to upload them individually
            // with bucket and key, S3 respond to request withCreateMultipartUploadResponse
            // method extracts uploadID token to know that all these pieces belong together to
            // [identify, upload, assemble and cancle/abort]
            if (uploadId == null) {
                uploadId = s3.createMultipartUpload(b -> b.bucket(bucket).key(key)).uploadId();
            }
            // determine part number
            int number = parts.size() + 1;
            // upload buffer bytes, ETag is identifier of completation returned by storage server for uploaded part, uploadID is must
            String etag = s3.uploadPart(
                    b -> b.bucket(bucket).key(key).uploadId(uploadId).partNumber(number),
                    RequestBody.fromByteBuffer(ByteBuffer.wrap(buffer, 0, filled))).eTag();
            // save completed part info and reset buffer counter
            parts.add(CompletedPart.builder().partNumber(number).eTag(etag).build());
            filled = 0;
        }catch (SdkException e) {
            // if failed, fast abort to not allow corrupt or half data
            abort();
            throw new IOException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (failed) return;
        try {
            // uploadID == null means it is less than 8 MB backup so no need to multipart, just send to garage
            if (uploadId == null) {
                s3.putObject(b -> b.bucket(bucket).key(key),
                        RequestBody.fromByteBuffer(ByteBuffer.wrap(buffer, 0, filled)));
            }else {
                // if not and it is bigger than 8 MB send to uploadPart for multipart it
                if (filled > 0) uploadPart();
                s3.completeMultipartUpload(b -> b
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(m -> m.parts(parts)));
            }
        }catch (SdkException e) {
            abort();
            throw new IOException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    // if upload fails, cancel multipart as a whole to prevent incomplete parts remain
    private void abort(){
        failed = true;    //mark it
        if (uploadId != null) return;
        try {
            s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId));
        }catch (SdkException e) {
            // throwing hides original exception to failure, so instead logging used
            log.warn("Couldn't abort multipart upload for {} (id {}): {}", key, uploadId, e.getMessage());
        }
    }
}
