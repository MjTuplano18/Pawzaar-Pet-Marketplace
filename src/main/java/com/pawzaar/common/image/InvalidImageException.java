package com.pawzaar.common.image;

/**
 * The uploaded file is empty or is not a recognisable image. Maps to {@code 400 Bad Request}:
 * the request is malformed, not merely an unsupported format.
 */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }
}
