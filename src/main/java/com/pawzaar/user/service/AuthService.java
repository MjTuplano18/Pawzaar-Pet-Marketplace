package com.pawzaar.user.service;

// Our own classes (com.pawzaar...), one import per file, because they live in different packages.
import com.pawzaar.user.EmailAlreadyRegisteredException;  // thrown when the email is taken
import com.pawzaar.user.User;                             // the entity this service creates
import com.pawzaar.user.dto.RegisterRequest;              // the request DTO coming from the controller
import com.pawzaar.user.dto.UserResponse;                 // the DTO we return to the controller
import com.pawzaar.user.repository.UserRepository;        // the data layer

// Spring Security's hashing interface - the BCrypt bean defined in PasswordEncoderConfig.
import org.springframework.security.crypto.password.PasswordEncoder;

// @Service = stereotype annotation: Spring creates this class at startup and can inject it
// into AuthController. Its job: hold the business rules.
import org.springframework.stereotype.Service;

// @Transactional = "wrap this method in one database transaction".
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;  // needed for toLowerCase(Locale.ROOT) - see the note in register(...)

/**
 * Business layer for the "auth" feature - the ONLY class allowed to talk to UserRepository.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>run database work inside a transaction;</li>
 *   <li>hold the business rules (email is unique, passwords are hashed, emails are normalized);</li>
 *   <li>translate entities into DTOs so {@code User} never leaves this layer.</li>
 * </ul>
 */
@Service
public class AuthService {

    // final = the dependency cannot be swapped after construction. private = only this class
    // may touch it. Spring fills both in through the constructor below.
    private final UserRepository userRepository;

    // Constructor INJECTION: with a single constructor, Spring injects automatically -
    // no @Autowired needed. This also makes unit testing easy: you can pass a fake repository.
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // A WRITE transaction this time (no readOnly flag - it defaults to false, and it must,
    // because this method inserts a row). If anything below throws, the insert is rolled back.
    @Transactional
    public UserResponse register(RegisterRequest request) {

        // NORMALIZE the email: "Ana@Gmail.com" and "ana@gmail.com" are the SAME account.
        // Without this you get two users who can never log in consistently.
        // Locale.ROOT avoids the Turkish-i bug, where "I".toLowerCase() becomes a dotless "ı".
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Business rule #1: an email may only be used once.
        // The exception carries no HTTP knowledge - GlobalExceptionHandler turns it into 409.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        // Business rule #2: never store a raw password.
        // The plaintext exists only inside this method and is never written anywhere.
        // User.register(...) is the only way to build a User, and it insists on a hash.
        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),   // <- HASHING happens exactly here
                request.displayName().trim()
        );

        // Optional field: only set it when the client actually sent one.
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }

        // save() = INSERT for a brand-new entity (it has no id yet);
        // it would be an UPDATE for an entity that already has one.
        User saved = userRepository.save(user);

        return toResponse(saved);   // entity -> DTO, exactly like PetService does
    }

    // Entity -> DTO. private static = only used here, and it needs no instance state.
    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRole(),
                user.isVerified(),   // Lombok's getter for a boolean field is "is..." not "get..."
                user.getCreatedAt()
        );
    }

}
