package com.pawzaar.pet.image;

/**
 * The upload's content type is not on the allowlist (or the declared type disagrees with the actual
 * bytes). Maps to {@code 415 Unsupported Media Type} - a recognised file was sent, just not one we
 * accept.
 */
public class UnsupportedImageTypeException extends RuntimeException {

    private final String contentType;

    public UnsupportedImageTypeException(String contentType) {
        super("Unsupported image type: " + (contentType == null || contentType.isBlank() ? "(none)" : contentType));
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
