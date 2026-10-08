package com.pawzaar.user;

/**
 * A DOMAIN EXCEPTION: it describes what went wrong in the USER feature using business
 * language, with zero knowledge of HTTP or JSON.
 *
 * <p>Compare the two layers:
 * <pre>
 *   this class:      "this email is already registered"      <- domain language
 *   the handler:     "turn that into 409 + RFC 9457 JSON"     <- HTTP language
 * </pre>
 * That separation is why the same exception can later be reused by a CLI, a message queue
 * consumer, or a GraphQL endpoint without changing a line.
 *
 * <p>WHY IT EXTENDS RuntimeException (unchecked): the compiler will NOT force every caller
 * to write try/catch. It can travel from AuthService up to GlobalExceptionHandler on its own.
 * Two bonus effects:
 * <ul>
 *   <li>@Transactional rolls back automatically for unchecked exceptions;</li>
 *   <li>your method signatures stay clean (no throws everywhere).</li>
 * </ul>
 * (Checked exceptions - "throws IOException" - are the opposite; use them only for errors
 * the caller can genuinely recover from.)
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    // Extra data the HTTP layer may want to show the client (it becomes {"email": ...} in JSON).
    private final String email;

    public EmailAlreadyRegisteredException(String email) {
        // super(...) = build the message that ex.getMessage() will return later.
        // This text ends up in the response body, so it must never contain a password.
        super("An account with email '" + email + "' already exists");
        this.email = email;
    }

    // A normal getter for our own field - nothing to do with Spring.
    public String getEmail() {
        return email;
    }

}
