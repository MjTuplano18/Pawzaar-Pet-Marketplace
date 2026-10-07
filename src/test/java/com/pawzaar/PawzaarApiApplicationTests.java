package com.pawzaar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: boots the ENTIRE Spring context (connects to PostgreSQL, runs Flyway,
 * validates every entity against the schema) and asserts that nothing blows up.
 * If any configuration is broken, contextLoads() fails.
 * Requires Docker Postgres to be running (docker compose up -d).
 */
@SpringBootTest
class PawzaarApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
