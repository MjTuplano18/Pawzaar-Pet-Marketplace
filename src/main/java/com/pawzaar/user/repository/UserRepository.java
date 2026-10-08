package com.pawzaar.user.repository;

// The entity this repository reads and writes. Not in the same package, so we import it.
import com.pawzaar.user.User;

// Spring Data's base repository interface. Extending it is what makes this file "magic":
// Spring generates the implementation at startup (findAll, save, deleteById, count...).
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;  // "a result that may be absent" - forces you to handle "not found"
import java.util.UUID;      // the type of User's primary key

/**
 * Data layer for users. We write ZERO SQL here.
 *
 * <p>Two arguments tell Spring Data everything:
 * <ul>
 *   <li>{@code User}  -> "which table?" (reads @Table(name = "users") off the entity)</li>
 *   <li>{@code UUID}  -> "which column is the key?" (reads @Id -> id)</li>
 * </ul>
 *
 * <p>Everything below is a DERIVED QUERY: Spring Data parses the method name and writes the
 * SQL. Typo a field name and the app refuses to start with a clear error - never silently
 * wrong SQL.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    // find + By + Email  ==>  SELECT * FROM users WHERE email = ?
    // Returns Optional because there may be no such user - the caller must check.
    Optional<User> findByEmail(String email);

    // exists + By + Email  ==>  SELECT count(*) > 0 FROM users WHERE email = ?
    // Used at registration: "is this email already taken?" (no row is loaded, so it is cheap).
    boolean existsByEmail(String email);
}
