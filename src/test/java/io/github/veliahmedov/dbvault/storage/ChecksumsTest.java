package io.github.veliahmedov.dbvault.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Unit tests: test if SHA-256 checksum implementation works as intended
public class ChecksumsTest {
    // from sha256 calculator, sha256 value of abc
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    // sha256 value of empty value
    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    // test if, abc should return intended sha256 value we gave
    @Test
    void knownHashOfInputStream() throws IOException {
        assertEquals(ABC, Checksums.sha256Of(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
    }

    // test if, empty value (no byte, zero) should return intended sha256 value we gave
    @Test
    void hashOfEmptyInput() throws IOException {
        assertEquals(EMPTY, Checksums.sha256Of(new ByteArrayInputStream(new byte[0])));
    }

    // backup process calc checksum while writing backup, test if it produces expected hash
    @Test
    void hashWhileWritingMatchesHashWhileReading() throws IOException {
        MessageDigest md = Checksums.sha256();
        try (OutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), md)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(ABC, Checksums.hex(md));
    }
}
