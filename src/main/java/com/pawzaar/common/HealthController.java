package com.pawzaar.common;

// Provides the Map collection used for the health response.
import java.util.Map;

// Maps HTTP GET requests to controller methods.
import org.springframework.web.bind.annotation.GetMapping;

// Defines the common URL prefix for all endpoints in this controller.
import org.springframework.web.bind.annotation.RequestMapping;

// Marks this class as a REST controller that returns data directly as HTTP responses.
import org.springframework.web.bind.annotation.RestController;


/**
 * Provides common API endpoints used across the application.
 *
 * Currently, this controller provides a health-check endpoint
 * that can be used to verify that the Pawzaar API is running.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    // Handles GET /api/v1/health and returns a simple status response.
    @GetMapping("/health")
    public Map<String, String> health() {

        // Returns JSON: {"status": "ok"}
        return Map.of("status", "ok");
    }
}