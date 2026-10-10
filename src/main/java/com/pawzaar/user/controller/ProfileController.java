package com.pawzaar.user.controller;

import com.pawzaar.common.image.ServedImage;
import com.pawzaar.user.dto.UpdateProfileRequest;
import com.pawzaar.user.dto.UserResponse;
import com.pawzaar.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The authenticated user's OWN profile, under {@code /api/v1/me}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/v1/me        -> 200 + the caller's profile (no password, ever)</li>
 *   <li>PUT    /api/v1/me        -> 200 + the updated profile (displayName, phone, bio)</li>
 *   <li>POST   /api/v1/me/avatar -> 200 + the profile (sets or replaces the picture)</li>
 *   <li>GET    /api/v1/me/avatar -> 200 + the avatar bytes (404 if none set)</li>
 *   <li>DELETE /api/v1/me/avatar -> 204 (idempotent)</li>
 * </ul>
 *
 * <p>All require a valid access token (the default "anyRequest().authenticated()" rule in
 * SecurityConfig). The identity comes from {@code @AuthenticationPrincipal Jwt}: Spring Security
 * hands us the VERIFIED token, and its {@code sub} claim is the user id — so the controller never
 * trusts a body field or header for "who am I".
 *
 * <p>THE THIN CONTROLLER RULE: parse the request, delegate to the service, return the DTO.
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final AuthService authService;

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getProfile(userId(jwt));
    }

    // @Valid runs the UpdateProfileRequest checks (displayName required, phone format, bio length)
    // here at the edge, before the service ever runs - a 400 arrives without touching the DB.
    @PutMapping
    public UserResponse update(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(userId(jwt), request);
    }

    // Multipart upload, same contract as pet images: field name "file", JPEG/PNG/WebP,
    // max 5 MiB (the servlet container enforces the same limit before our code runs).
    // Uploading again simply REPLACES the picture - the service removes the old file.
    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                     @RequestPart("file") MultipartFile file) {
        return authService.setAvatar(userId(jwt), file);
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal Jwt jwt) {
        authService.removeAvatar(userId(jwt));
        return ResponseEntity.noContent().build();   // 204 - and 204 again even if none was set
    }

    // Streams the avatar bytes. Content-Type is the one verified at upload time (magic-byte sniffed),
    // not a client-supplied guess. private cache: this is the caller's own picture.
    @GetMapping("/avatar")
    public ResponseEntity<Resource> getAvatar(@AuthenticationPrincipal Jwt jwt) {
        ServedImage avatar = authService.getAvatar(userId(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(avatar.resource());
    }

    /** The JWT subject ("sub") is the user id we stamped in when issuing the token. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}