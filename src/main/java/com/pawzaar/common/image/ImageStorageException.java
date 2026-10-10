package com.pawzaar.common.image;

/**
 * An infrastructure failure while reading or writing image bytes (disk full, no permission, ...).
 * This is a "should not normally happen" error, so the {@code GlobalExceptionHandler} renders it as
 * a plain {@code 500} - the client did nothing wrong and has nothing to fix.
 */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message) {
        super(message);
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
