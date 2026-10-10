package com.pawzaar.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H2: behind a TLS-terminating proxy (Render / Railway / a load balancer) every request arrives
 * from the proxy's IP. If the app treats that socket IP as "the client", ALL users share ONE rate
 * bucket (e.g. 20 requests/minute globally) - enough to lock everyone out of login at once.
 *
 * <p>The production profile must therefore opt in to forwarded headers DELIBERATELY:
 * {@code server.forward-headers-strategy: native} asks the servlet container to apply
 * {@code X-Forwarded-For} to {@code getRemoteAddr()}, so the rate-limiter keys on the real client.
 * (This is only safe because platform proxies overwrite that header before it reaches the app.)
 *
 * <p>This test pins that config in application-prod.yaml so the setting can never silently regress.
 */
class ProdForwardedHeadersStrategyTest {

    @Test
    void prodConfigTrustsForwardedHeadersDeliberately() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yaml"));

        Map<String, Object> flat = flatten(sources);
        // The loader wraps plain strings in OriginTrackedValue; String.valueOf unwraps it.
        assertEquals("native", String.valueOf(flat.get("server.forward-headers-strategy")),
                "application-prod.yaml must enable server.forward-headers-strategy=native "
                        + "(H2: without it every user behind the proxy shares one rate-limit bucket)");
    }

    /** YamlPropertySourceLoader already flattens nested maps to dotted keys; merge the sources. */
    private static Map<String, Object> flatten(List<PropertySource<?>> sources) {
        java.util.HashMap<String, Object> merged = new java.util.HashMap<>();
        sources.forEach(source -> merged.putAll((Map<String, Object>) source.getSource()));
        return merged;
    }
}