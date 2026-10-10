package com.pawzaar.common.image;

/**
 * An infrastructure failure while reading or writing image bytes (disk full, no permission, ...).
 * This is a failure of an UPSTREAM dependency, not of the client or this API, so the
 * {@code GlobalExceptionHandler} renders it as {@code 502 Bad Gateway} and logs it (H8/M10).
 */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message) {
        super(message);
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
