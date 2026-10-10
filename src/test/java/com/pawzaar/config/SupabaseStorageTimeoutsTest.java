package com.pawzaar.config;

import com.pawzaar.common.image.ImageStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H8: Supabase HTTP calls must carry explicit timeouts.
 *
 * <p>Without them the JDK client waits (effectively) forever, so an unreachable or slow Supabase
 * pins request threads until the pool is exhausted. These tests prove the configured connect and
 * read timeouts are actually applied to the client the storage bean uses.
 */
class SupabaseStorageTimeoutsTest {

    @Test
    void timeoutsHaveSensibleDefaults() {
        ImageStorageProperties.Supabase config = new ImageStorageProperties.Supabase();

        assertNotNull(config.getConnectTimeout());
        assertNotNull(config.getReadTimeout());
        assertTrue(config.getConnectTimeout().toSeconds() > 0, "connect timeout must be positive");
        assertTrue(config.getReadTimeout().toSeconds() > 0, "read timeout must be positive");
    }

    @Test
    void supabaseClientAppliesConfiguredConnectAndReadTimeouts() throws Exception {
        ImageStorageProperties.Supabase config = new ImageStorageProperties.Supabase();
        config.setUrl("https://example.supabase.co");
        config.setServiceKey("service-key");
        config.setConnectTimeout(Duration.ofSeconds(3));
        config.setReadTimeout(Duration.ofSeconds(7));

        JdkClientHttpRequestFactory factory = StorageConfig.requestFactory(config);

        // The connect timeout lives on the JDK HttpClient the factory wraps.
        HttpClient httpClient = (HttpClient) readField(factory, "httpClient");
        assertEquals(Duration.ofSeconds(3), httpClient.connectTimeout().orElseThrow());

        // The read timeout is set directly on the factory.
        assertEquals(Duration.ofSeconds(7), readField(factory, "readTimeout"));
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
