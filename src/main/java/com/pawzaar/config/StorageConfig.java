package com.pawzaar.config;

import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageStorageProperties;
import com.pawzaar.common.image.ImageValidator;
import com.pawzaar.common.image.LocalImageStorage;
import com.pawzaar.common.image.SupabaseImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Wires the image storage beans. The interface ({@link ImageStorage}) is what the service depends
 * on; the implementation is chosen here from {@code pawzaar.storage.type}:
 *
 * <ul>
 *   <li>{@code local} (default) - {@link LocalImageStorage}, files on disk. Keeps a fresh clone and
 *       the test suite running with no cloud account and no credentials.</li>
 *   <li>{@code supabase} - {@link SupabaseImageStorage}, backed by a Supabase Storage bucket.</li>
 * </ul>
 *
 * <p>Swapping backends is therefore one environment variable, not a code change - which is the whole
 * point of hiding storage behind the interface.
 */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(ImageStorageProperties properties) {
        if (properties.getType() == ImageStorageProperties.Type.SUPABASE) {
            return supabaseImageStorage(properties.getSupabase());
        }
        return new LocalImageStorage(properties.getRoot());
    }

    /**
     * Builds the Supabase-backed store, or fails startup with a clear message if it is selected
     * without credentials. Failing fast beats a confusing {@code NullPointerException} on the first
     * upload - a misconfigured deployment should refuse to start, not accept traffic it cannot serve.
     */
    private static ImageStorage supabaseImageStorage(ImageStorageProperties.Supabase config) {
        if (isBlank(config.getUrl()) || isBlank(config.getBucket()) || isBlank(config.getServiceKey())) {
            throw new IllegalStateException(
                    "pawzaar.storage.type=supabase requires SUPABASE_URL, SUPABASE_BUCKET and "
                            + "SUPABASE_SERVICE_KEY to be set");
        }
        RestClient client = RestClient.builder()
                .baseUrl(config.getUrl())
                // The service-role key is the bearer token; 'apikey' is sent too for the Storage API.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getServiceKey())
                .defaultHeader("apikey", config.getServiceKey())
                .build();
        return new SupabaseImageStorage(client, config.getBucket());
    }

    @Bean
    public ImageValidator imageValidator(ImageStorageProperties properties) {
        return new ImageValidator(properties.getMaxImageBytes());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
