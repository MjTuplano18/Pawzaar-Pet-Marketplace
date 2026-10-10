package com.pawzaar.common.image;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores images as files in a configured directory. Good enough for a single-instance deployment,
 * and simple to reason about: no external service to run, each image is one file.
 *
 * <p>Two safety properties:
 * <ul>
 *   <li><b>Server-generated names.</b> The key is a random UUID plus a validated extension, so a
 *       client cannot influence the path at all.</li>
 *   <li><b>Defence in depth.</b> {@link #resolveSafely} still rejects any key that would resolve
 *       outside the root, so even a future bug that passes through user input cannot escape.</li>
 * </ul>
 */
public class LocalImageStorage implements ImageStorage {

    private final Path root;

    public LocalImageStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] data, String extension) {
        String key = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolveSafely(key);
        try {
            Files.createDirectories(root);
            Files.write(target, data);
        } catch (IOException e) {
            throw new ImageStorageException("Could not store image", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path target = resolveSafely(key);
        if (!Files.isRegularFile(target)) {
            throw new ImageStorageException("Image not found");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException e) {
            throw new ImageStorageException("Could not delete image", e);
        }
    }

    /**
     * Resolves a key under the root and refuses anything that escapes it. {@code normalize()} collapses
     * {@code ..} segments, and the {@code startsWith} check rejects the result if it left the root.
     */
    private Path resolveSafely(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ImageStorageException("Rejected unsafe storage key");
        }
        return resolved;
    }
}
