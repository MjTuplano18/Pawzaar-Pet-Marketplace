package com.pawzaar.config;

import com.pawzaar.common.image.ImageStorageProperties;
import com.pawzaar.common.image.LocalImageStorage;
import com.pawzaar.common.image.SupabaseImageStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the backend selection in {@link StorageConfig}: local by default, Supabase only when asked
 * for and fully configured.
 */
class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void localIsTheDefault() {
        assertInstanceOf(LocalImageStorage.class, config.imageStorage(new ImageStorageProperties()));
    }

    @Test
    void supabaseWithoutCredentialsFailsFast() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setType(ImageStorageProperties.Type.SUPABASE);

        assertThrows(IllegalStateException.class, () -> config.imageStorage(properties));
    }

    @Test
    void supabaseWithCredentialsBuildsTheSupabaseStore() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setType(ImageStorageProperties.Type.SUPABASE);
        properties.getSupabase().setUrl("https://demo.supabase.co");
        properties.getSupabase().setBucket("pawzaar-images");
        properties.getSupabase().setServiceKey("service-key");

        assertInstanceOf(SupabaseImageStorage.class, config.imageStorage(properties));
    }
}
