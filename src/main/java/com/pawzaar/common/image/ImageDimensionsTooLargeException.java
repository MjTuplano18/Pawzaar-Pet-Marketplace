package com.pawzaar.common.image;

/**
 * The uploaded image is a valid format but its PIXEL dimensions are too large (M1).
 *
 * <p>Maps to {@code 413 Payload Too Large}: the request is well-formed, the client simply sent an
 * image bigger than we are willing to decode. Rejecting on the header (before decoding) is what
 * stops a small "decompression bomb" file from exhausting memory.
 */
public class ImageDimensionsTooLargeException extends RuntimeException {

    private final int width;
    private final int height;

    public ImageDimensionsTooLargeException(int width, int height, String reason) {
        super("Image dimensions are too large: " + width + "x" + height + " (" + reason + ")");
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
