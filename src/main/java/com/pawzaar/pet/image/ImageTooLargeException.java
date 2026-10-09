package com.pawzaar.pet.image;

/**
 * The upload exceeds the configured size limit. Maps to {@code 413 Payload Too Large} rather than a
 * generic 400 so the client can tell "your file is too big" apart from "your file is malformed".
 */
public class ImageTooLargeException extends RuntimeException {

    private final long sizeBytes;
    private final long maxBytes;

    public ImageTooLargeException(long sizeBytes, long maxBytes) {
        super("Image is too large: " + sizeBytes + " bytes (max " + maxBytes + " bytes)");
        this.sizeBytes = sizeBytes;
        this.maxBytes = maxBytes;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
