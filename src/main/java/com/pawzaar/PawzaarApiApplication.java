package com.pawzaar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>{@code @SpringBootApplication} = {@code @Configuration} + {@code @EnableAutoConfiguration}
 * + {@code @ComponentScan}: it tells Spring to scan everything under {@code com.pawzaar} for
 * beans, and to auto-configure the rest (embedded Tomcat, DataSource, Jackson, Hibernate, ...)
 * based on the dependencies found in pom.xml.
 */
@SpringBootApplication
public class PawzaarApiApplication {

	public static void main(String[] args) {
		// Bootstraps the Spring context (creates all beans, runs Flyway, validates the schema)
		// and then starts the embedded Tomcat server on port 8080.
		SpringApplication.run(PawzaarApiApplication.class, args);
	}

}
