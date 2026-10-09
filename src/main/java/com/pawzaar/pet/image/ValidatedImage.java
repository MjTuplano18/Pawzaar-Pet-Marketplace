package com.pawzaar.pet.image;

/**
 * The outcome of validating an upload: the exact bytes to store, a safe extension, and the verified
 * MIME type. Returning the bytes here means the file is read from the request exactly once.
 */
public record ValidatedImage(byte[] data, String extension, String contentType) {
}
