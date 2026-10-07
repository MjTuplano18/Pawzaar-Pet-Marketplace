package com.pawzaar.pet;

/**
 * Lifecycle of a listing: ACTIVE (visible to buyers) / SOLD / HIDDEN (taken down by
 * the seller) / PENDING_REVIEW (flagged, waiting for an admin).
 */
public enum PetStatus {
    ACTIVE, SOLD, HIDDEN, PENDING_REVIEW
}
