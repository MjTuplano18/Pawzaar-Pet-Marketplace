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

import java.util.Locale;

/**
 * Wires the image storage beans behind the {@link ImageStorage} interface; the implementation is
 * chosen here from {@code pawzaar.storage.type}:
 *
 * <ul>
 *   <li>{@code local} (default) - {@link LocalImageStorage}, files on disk. Keeps a fresh clone and
 *       the test suite running with no cloud account and no credentials.</li>
 *   <li>{@code supabase} - {@link SupabaseImageStorage}, backed by a Supabase Storage bucket.</li>
 * </ul>
 *
 * <p>There is ONE bean per CONTENT TYPE, because Supabase keeps each kind in its own bucket:
 *
 * <ul>
 *   <li>{@code petImageStorage}      -> pet listing photos       ({@code pawzaar-images})</li>
 *   <li>{@code profileImageStorage}  -> user avatars             ({@code pawzaar-user-profile})</li>
 *   <li>{@code documentImageStorage} -> pet documents, e.g. vaccine cards ({@code pawzaar-docs})</li>
 * </ul>
 *
 * <p>Services pick their store by name with {@code @Qualifier} (the pet feature takes
 * {@code petImageStorage}; the avatar feature will take {@code profileImageStorage}). In LOCAL mode
 * each kind gets its own subfolder under the configured root ({@code uploads/images}, ...) so dev
 * files never mix; in SUPABASE mode each bean targets its own bucket.
 */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class StorageConfig {

    @Bean
    public ImageStorage petImageStorage(ImageStorageProperties properties) {
        return storageFor(properties, StorageKind.IMAGES);
    }

    @Bean
    public ImageStorage profileImageStorage(ImageStorageProperties properties) {
        return storageFor(properties, StorageKind.PROFILES);
    }

    @Bean
    public ImageStorage documentImageStorage(ImageStorageProperties properties) {
        return storageFor(properties, StorageKind.DOCUMENTS);
    }

    /** One builder, three uses - {@link StorageKind} decides which bucket/folder each bean owns. */
    private static ImageStorage storageFor(ImageStorageProperties properties, StorageKind kind) {
        if (properties.getType() == ImageStorageProperties.Type.SUPABASE) {
            return supabaseImageStorage(properties.getSupabase(), kind);
        }
        // Per-kind subfolder, so avatars and pet photos never share a directory in dev either.
        return new LocalImageStorage(properties.getRoot().resolve(kind.folder));
    }

    /**
     * Builds the Supabase-backed store for one content type, or fails startup with a clear message if
     * supabase is selected without credentials. Failing fast beats a confusing
     * {@code NullPointerException} on the first upload - a misconfigured deployment should refuse to
     * start, not accept traffic it cannot serve.
     */
    private static ImageStorage supabaseImageStorage(ImageStorageProperties.Supabase config, StorageKind kind) {
        String bucket = switch (kind) {
            case IMAGES -> config.getBucket();
            case PROFILES -> config.getProfileBucket();
            case DOCUMENTS -> config.getDocumentBucket();
        };
        if (isBlank(config.getUrl()) || isBlank(bucket) || isBlank(config.getServiceKey())) {
            throw new IllegalStateException(
                    "pawzaar.storage.type=supabase requires SUPABASE_URL, SUPABASE_SERVICE_KEY and the "
                            + kind.name().toLowerCase(Locale.ROOT) + " bucket (SUPABASE_BUCKET / "
                            + "SUPABASE_PROFILE_BUCKET / SUPABASE_DOCUMENT_BUCKET) to be set");
        }
        RestClient client = RestClient.builder()
                .baseUrl(config.getUrl())
                // The service-role key is the bearer token; 'apikey' is sent too for the Storage API.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getServiceKey())
                .defaultHeader("apikey", config.getServiceKey())
                .build();
        return new SupabaseImageStorage(client, bucket);
    }

    @Bean
    public ImageValidator imageValidator(ImageStorageProperties properties) {
        return new ImageValidator(properties.getMaxImageBytes());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The content types the buckets hold; the folder name doubles as the LOCAL subfolder. */
    private enum StorageKind {
        IMAGES("images"),
        PROFILES("profiles"),
        DOCUMENTS("documents");

        private final String folder;

        StorageKind(String folder) {
            this.folder = folder;
        }
    }
}