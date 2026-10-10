package com.pawzaar.user;

// === jakarta.persistence = "how this class maps to a database table" ===
import jakarta.persistence.Entity;        // marks the class as a JPA entity (a row in a table)
import jakarta.persistence.Table;         // sets the table name (default would be "user" - a reserved word!)
import jakarta.persistence.Id;            // marks the primary key field
import jakarta.persistence.GeneratedValue; // "generate a value for me automatically"
import jakarta.persistence.GenerationType; // WHICH strategy: UUID, IDENTITY, AUTO, SEQUENCE...
import jakarta.persistence.Column;        // describes one column (name, length, nullable, unique)
import jakarta.persistence.Enumerated;    // "this field holds an enum" (Java type -> VARCHAR)
import jakarta.persistence.EnumType;      // which storage: STRING (names) or ORDINAL (0,1,2)
import jakarta.persistence.PrePersist;   // lifecycle callback: run me just before INSERT

// === lombok = less boilerplate, same Java underneath ===
import lombok.Getter;                     // generates getXxx() for every field
import lombok.Setter;                     // generates setXxx(...) - we apply it FIELD BY FIELD
import lombok.NoArgsConstructor;           // generates the no-arg constructor Hibernate needs
import lombok.AccessLevel;                // lets us control that constructor's visibility (PROTECTED)

// === plain Java types ===
import java.time.Instant;  // a moment in time -> maps to TIMESTAMPTZ
import java.util.UUID;     // 128-bit id -> maps to the UUID primary key

/**
 * JPA entity for the `users` table (created by migration V2). Owns identity and credentials:
 * email, the BCrypt hash of the password, display name, phone, role and verification flag.
 *
 * <p>Lombok rules for entities in this project:
 * <ul>
 *   <li>{@code @Getter} on the CLASS - reading fields from anywhere is safe</li>
 *   <li>{@code @Setter} only on EDITABLE fields - id, passwordHash and createdAt are frozen</li>
 *   <li>{@code @NoArgsConstructor(PROTECTED)} - Hibernate needs a no-arg constructor to build
 *       instances when it reads rows; PROTECTED stops application code doing {@code new User()}</li>
 *   <li>NEVER {@code @Data} / {@code @ToString} / {@code @EqualsAndHashCode} - they would drag
 *       every field (including passwordHash) into toString/equals and cause surprise SQL</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    // GenerationType.UUID tells Hibernate to generate the id in Java before the INSERT.
    // (Rows created by raw SQL, like the V3 seed, use the table's DEFAULT gen_random_uuid().)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // UNIQUE in the database: UserRepository.existsByEmail relies on this rule to answer
    // "is this email already taken?" with a single cheap query.
    @Setter
    @Column(nullable = false, unique = true)
    private String email;

    // NO @Setter, deliberately. A BCrypt hash must never be swapped out by accident, and no
    // code outside registration should be able to "change" it. It is set exactly once, by the
    // static factory register(...) at the bottom of this class.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Setter
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // No nullable = false here: phone is OPTIONAL (null is allowed, and JSON shows "phone": null).
    @Setter
    @Column(length = 30)
    private String phone;

    // Same idea as phone: a short optional "about me" text. NULL until the user writes one.
    @Setter
    @Column(length = 500)
    private String bio;

    // A user has exactly ONE avatar. We store the opaque storage key and the verified MIME type
    // (needed to set Content-Type when serving the bytes). The key itself is NEVER serialized -
    // the API only exposes a derived URL. NULL = no avatar yet.
    @Setter
    @Column(name = "avatar_storage_key", length = 255)
    private String avatarStorageKey;

    @Setter
    @Column(name = "avatar_content_type", length = 50)
    private String avatarContentType;

    // EnumType.STRING stores "USER"/"SELLER" instead of 0/1. Ordinals would corrupt data the
    // moment someone inserted a new constant in the middle of the enum.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;   // matches the DB default 'USER'

    // Primitive boolean (never Boolean) so it can never be null.
    // M4d: flipped by markVerified() once the user proves control of the address via the emailed
    // link. Nothing in the app is GATED on this flag yet - it is informational.
    @Column(nullable = false)
    private boolean verified = false;

    // updatable = false -> JPA will never include this column in an UPDATE statement.
    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The ONLY way to create a User: the password must arrive ALREADY hashed, because
     * passwordHash deliberately has no setter.
     *
     * <p>Why a static factory instead of setters? Setters let any caller assemble an object in
     * any order, including skipping required fields. A factory encodes the rule "you cannot
     * have a User without an email, a hash and a display name" in one place.
     *
     * <p>It is STATIC, so the entity needs no injected Spring beans - domain objects stay
     * free of framework dependencies (that is the job of AuthService).
     */
    public static User register(String email, String passwordHash, String displayName) {
        User user = new User();            // legal HERE but not outside: the constructor is
                                           // protected, and we are inside the same class
        user.email = email;                // same reason we may set the fields that have no setter
        user.passwordHash = passwordHash;  // <- the hash, never the raw password
        user.displayName = displayName;
        return user;                       // role and verified already default to USER / false
    }

    /**
     * M4d: marks the account email-verified, after the user opened the link sent to that address.
     * A domain method (not a setter) so the only way to flip the flag is to pass through this
     * intention-revealing call.
     */
    public void markVerified() {
        this.verified = true;
    }

    /**
     * Replaces the stored password hash after a verified password reset. The hash must arrive
     * ALREADY hashed (AuthService runs the encoder); a domain method rather than a setter keeps the
     * "no raw password ever reaches the entity" rule and makes the intent explicit at the call site.
     */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    // Lifecycle callback: Spring/Hibernate calls this automatically just before every INSERT,
    // so no caller can forget to set the timestamp.
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
