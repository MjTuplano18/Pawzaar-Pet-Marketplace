package com.pawzaar.pet;

import jakarta.persistence.Column; // Configures database column properties.
import jakarta.persistence.Entity; // Marks this class as a JPA entity.
import jakarta.persistence.EnumType; // Defines how enum values are stored.
import jakarta.persistence.Enumerated; // Maps Java enums to database columns.
import jakarta.persistence.GeneratedValue; // Configures automatic ID generation.
import jakarta.persistence.GenerationType; // Defines the ID generation strategy.
import jakarta.persistence.Id; // Marks the primary key of the entity.
import jakarta.persistence.PrePersist; // Runs a method before a new entity is inserted.
import jakarta.persistence.Table; // Specifies the database table name.

import lombok.AccessLevel; // Provides access-level options for Lombok.
import lombok.Getter; // Generates getter methods automatically.
import lombok.NoArgsConstructor; // Generates a no-argument constructor.
import lombok.Setter; // Generates setter methods for editable fields.

import java.math.BigDecimal; // Used for accurate monetary values.
import java.time.Instant; // Represents the creation date and time.
import java.util.UUID; // Used for unique identifiers.


/**
 * JPA entity representing a pet listing in the database.
 *
 * Maps to the "pets" table and represents the data
 * stored for each pet listed on Pawzaar.
 *
 * Database Entity → PetService → DTO → Controller → Frontend
 */
@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet {

    // Primary key generated automatically by Hibernate.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    // ID of the user who owns or created the pet listing.
    // Kept as a UUID for now; this can become a relationship
    // with a User entity when the seller feature is implemented.
    @Setter
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;


    // Title displayed for the pet listing.
    @Setter
    @Column(nullable = false, length = 150)
    private String title;


    // Species of the pet, such as DOG or CAT.
    // STRING stores the enum name instead of its numeric position.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Species species;


    // Breed of the pet.
    @Setter
    private String breed;


    // Age of the pet in months.
    @Setter
    @Column(name = "age_months", nullable = false)
    private int ageMonths;


    // Asking price of the pet.
    // BigDecimal is used because it is more accurate for monetary values.
    @Setter
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;


    // Detailed description of the pet listing.
    // Stored as TEXT to support longer descriptions.
    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;


    // City where the pet is located.
    @Setter
    @Column(nullable = false)
    private String city;


    // Province where the pet is located.
    @Setter
    @Column(nullable = false)
    private String province;


    // Current status of the pet listing.
    // New pets start with ACTIVE status by default.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PetStatus status = PetStatus.ACTIVE;


    // Date and time when the pet listing was created.
    // updatable = false prevents this value from being changed later.
    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    // Automatically sets the creation timestamp before inserting a new pet.
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}