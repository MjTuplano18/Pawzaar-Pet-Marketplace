package com.pawzaar.pet.dto;

import java.util.UUID;

/**
 * One image belonging to a listing.
 *
 * <p>Note what is <em>not</em> here: the storage key. Clients never learn where a file physically
 * lives. They get an opaque {@code id} and a {@code url} to fetch it through the API, so storage can
 * be moved or locked down later without changing the contract.
 */
public record PetImageResponse(
        UUID id,
        String url,
        String contentType,
        int sortOrder
) {}
