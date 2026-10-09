package com.pawzaar.pet.image;

import org.springframework.core.io.Resource;

/**
 * An image's bytes plus its MIME type, ready to be streamed back to a client.
 */
public record ServedImage(Resource resource, String contentType) {
}
