package io.github.veliahmedov.dbvault.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// utility class for checking if there is any corruption via SHA-256 hash strings
// it takes backup hashes to compare for any change
public final class Checksums {
    private Checksums(){}

    // MessageDigest used to calculate current hash of SHA-256
    public static MessageDigest sha256(){
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
    // digest finalize calculation and return hash as bytes,
    // HexFormat convert those bytes into readable hexadecimal string
    // raw bytes aren't good for storing
    public static String hex(MessageDigest digest){
        return HexFormat.of().formatHex(digest.digest());
    }

    // method that calculates hash of existing file or stream
    public static String sha256Of(InputStream input) throws IOException {
        //algorithm for calculation
        MessageDigest digest = sha256();
        // wrap input stream with DigestInputStream, when data passes, it is auto fed to MessageDigest
        try (DigestInputStream dis = new DigestInputStream(input, digest)) {
            // read data and send it to  special out stream that discard all because we only need calc hash, no need a copy
            dis.transferTo(OutputStream.nullOutputStream());
        }
        // return calculated SHA-256 hash in readable hexadecimal string format
        return  hex(digest);
    }
}
