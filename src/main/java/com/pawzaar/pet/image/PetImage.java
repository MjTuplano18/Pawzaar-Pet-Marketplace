package com.pawzaar.pet.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored image belonging to a pet listing (table {@code pet_images}, migration V7).
 *
 * <p>Follows the project's entity rules: {@code @Getter} only, no public constructor (use the
 * {@link #create} factory), and no {@code @Data}/{@code @ToString} so lazy fields can never be
 * accidentally serialised.
 *
 * <p>{@code petId} is a plain UUID rather than a JPA {@code @ManyToOne}. That keeps the two
 * aggregates decoupled: an image never needs to walk to its pet, and the service already has the
 * pet in hand when it needs one. The database still enforces the relationship with a foreign key.
 */
@Entity
@Table(name = "pet_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    /** Opaque key handed to {@link ImageStorage}; never exposed over the API. */
    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /** 0 is the cover image; higher numbers follow on the detail page. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Factory - the only supported way to create an image row. */
    public static PetImage create(UUID petId, String storageKey, String contentType, long sizeBytes, int sortOrder) {
        PetImage image = new PetImage();
        image.petId = petId;
        image.storageKey = storageKey;
        image.contentType = contentType;
        image.sizeBytes = sizeBytes;
        image.sortOrder = sortOrder;
        return image;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
