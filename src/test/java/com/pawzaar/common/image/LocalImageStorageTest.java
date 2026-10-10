package com.pawzaar.common.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the local filesystem storage. A JUnit {@link TempDir} keeps them hermetic and
 * self-cleaning - no writes to the real uploads directory.
 */
class LocalImageStorageTest {

    @TempDir
    Path tempDir;

    private final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Test
    void storeThenLoadReturnsTheSameBytes() throws IOException {
        LocalImageStorage storage = new LocalImageStorage(tempDir);

        String key = storage.store(png, "png");

        assertTrue(key.endsWith(".png"), "key should carry the validated extension: " + key);
        Resource loaded = storage.load(key);
        assertArrayEquals(png, loaded.getInputStream().readAllBytes());
    }

    @Test
    void eachStoreGetsItsOwnKey() {
        LocalImageStorage storage = new LocalImageStorage(tempDir);

        String first = storage.store(png, "png");
        String second = storage.store(png, "png");

        assertNotEquals(first, second, "keys must be unique so uploads never overwrite each other");
    }

    @Test
    void loadingAMissingKeyFails() {
        LocalImageStorage storage = new LocalImageStorage(tempDir);
        assertThrows(ImageStorageException.class, () -> storage.load("does-not-exist.png"));
    }

    @Test
    void deleteRemovesTheFile() {
        LocalImageStorage storage = new LocalImageStorage(tempDir);
        String key = storage.store(png, "png");

        storage.delete(key);

        assertThrows(ImageStorageException.class, () -> storage.load(key));
        // Deleting again is a harmless no-op, not an error.
        storage.delete(key);
    }

    @Test
    void rejectsKeysThatTryToEscapeTheRoot() {
        LocalImageStorage storage = new LocalImageStorage(tempDir);

        // Defence in depth: we generate keys ourselves, but a traversal attempt must still be refused.
        assertThrows(ImageStorageException.class, () -> storage.load("../../etc/passwd"));
        assertThrows(ImageStorageException.class, () -> storage.delete("../../etc/passwd"));
    }
}
