package com.pawzaar.pet.image;

import java.util.UUID;

/**
 * No image with the given id belongs to the given pet. Maps to {@code 404}.
 *
 * <p>The id is scoped to a pet on purpose: looking up an image by id alone would let a caller reach
 * another listing's image by guessing. The repository query always filters on both.
 */
public class PetImageNotFoundException extends RuntimeException {

    private final UUID imageId;

    public PetImageNotFoundException(UUID imageId) {
        super("No image exists with id " + imageId);
        this.imageId = imageId;
    }

    public UUID getImageId() {
        return imageId;
    }
}
