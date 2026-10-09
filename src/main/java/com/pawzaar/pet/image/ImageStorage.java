package com.pawzaar.pet.image;

import org.springframework.core.io.Resource;

/**
 * Stores and retrieves uploaded image bytes, hiding <em>where</em> they physically live.
 *
 * <p>Keeping this an interface matters: the rest of the app must not care whether files sit on the
 * local disk (this project), in S3, or behind a CDN. Swapping {@link LocalImageStorage} for a cloud
 * implementation later touches one wiring line, not the service or controller.
 *
 * <p>Keys are <b>opaque, server-generated identifiers</b>, never client filenames. An attacker who
 * controls a key could otherwise escape the storage directory ("../../etc/passwd"); by generating
 * keys ourselves and refusing anything suspicious on read, path traversal is impossible.
 */
public interface ImageStorage {

    /**
     * Persist the bytes under a fresh, unguessable key.
     *
     * @param data      the validated image bytes
     * @param extension the file extension to use, e.g. {@code jpg} (no dot)
     * @return the storage key that can later be passed to {@link #load} / {@link #delete}
     */
    String store(byte[] data, String extension);

    /**
     * Load stored bytes by key.
     *
     * @throws ImageStorageException if no image exists for the key
     */
    Resource load(String key);

    /** Delete by key. A no-op if the image is already gone. */
    void delete(String key);
}
