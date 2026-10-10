package com.pawzaar.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C1: the developer seed account must never reach production.
 *
 * <p>Runs Flyway in two throwaway schemas against the local test PostgreSQL:
 * the PRODUCTION migration set (only {@code classpath:db/migration}) and the DEV set
 * (migration + a dev-only sample-data folder configured in application-dev.yaml). Verifies:
 * <ul>
 *   <li>prod migrations leave NO seed user or pets behind</li>
 *   <li>dev migrations still provide the sample seller and its pets</li>
 * </ul>
 * Each schema is cleaned up afterwards, so the shared database is never polluted and
 * the test is order-independent.
 */
class FlywaySeedLocationsTest {

    private static final String URL  = "jdbc:postgresql://localhost:5433/pawzaar";
    private static final String USER = "pawzaar";
    private static final String PASS = "pawzaar_dev";

    private static final String SEED_EMAIL = "seed@pawzaar.test";
    private static final String SEED_ID    = "11111111-1111-1111-1111-111111111111";

    @Test
    void prodMigrationsLeaveNoSeedAccount() throws Exception {
        migrateAndCheck("flyway_prod_check",
                new String[]{"classpath:db/migration"},    // prod = the default single location
                0, 0);
    }

    @Test
    void devMigrationsStillProvideSampleData() throws Exception {
        migrateAndCheck("flyway_dev_check",
                new String[]{"classpath:db/migration", "classpath:db/migration-dev"},
                1, 3);
    }

    private static void migrateAndCheck(String schema, String[] locations,
                                        long expectedSeedUsers, long expectedPets) throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASS)
                .locations(locations)
                .schemas(schema)          // its own schema: never touches the shared DB
                .cleanDisabled(false)
                .load();
        try {
            flyway.clean();               // drop the throwaway schema if a previous run left it
            flyway.migrate();

            try (Connection con = DriverManager.getConnection(URL, USER, PASS);
                 PreparedStatement users = con.prepareStatement(
                         "SELECT count(*) FROM " + schema + ".users WHERE id = ?::uuid");
                 PreparedStatement pets = con.prepareStatement(
                         "SELECT count(*) FROM " + schema + ".pets WHERE seller_id = ?::uuid")) {
                users.setString(1, SEED_ID);
                pets.setString(1, SEED_ID);

                assertEquals(expectedSeedUsers, count(users),
                        "seed users in schema " + schema + " (email " + SEED_EMAIL + ")");
                assertEquals(expectedPets, count(pets),
                        "seed pets in schema " + schema);
            }
        } finally {
            flyway.clean();               // always leave the shared database untouched
        }
    }

    private static long count(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}