package com.pawzaar.config;

import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageStorageProperties;
import com.pawzaar.common.image.LocalImageStorage;
import com.pawzaar.common.image.SupabaseImageStorage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the backend selection in {@link StorageConfig}: local by default, Supabase only when asked
 * for and fully configured - and each of the three content beans landing in its own bucket/folder.
 */
class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void localIsTheDefaultForEveryContentType() {
        // Defaults: LOCAL backend, one bean per content type in its own subfolder, so avatars,
        // pet photos and documents never mix - not even in dev.
        assertLocal(config.petImageStorage(new ImageStorageProperties()), "uploads/images");
        assertLocal(config.profileImageStorage(new ImageStorageProperties()), "uploads/profiles");
        assertLocal(config.documentImageStorage(new ImageStorageProperties()), "uploads/documents");
    }

    @Test
    void supabaseWithoutCredentialsFailsFast() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setType(ImageStorageProperties.Type.SUPABASE);

        assertThrows(IllegalStateException.class, () -> config.petImageStorage(properties));
    }

    @Test
    void supabaseTargetsTheMatchingBucketPerContentType() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setType(ImageStorageProperties.Type.SUPABASE);
        properties.getSupabase().setUrl("https://demo.supabase.co");
        properties.getSupabase().setBucket("pawzaar-images");
        properties.getSupabase().setProfileBucket("pawzaar-user-profile");
        properties.getSupabase().setDocumentBucket("pawzaar-docs");
        properties.getSupabase().setServiceKey("service-key");

        // Each bean must be a SupabaseImageStorage aimed at ITS bucket - a profile picture stored
        // into the pet-photos bucket would be a silent data-mixing bug.
        SupabaseImageStorage pets = (SupabaseImageStorage) config.petImageStorage(properties);
        SupabaseImageStorage profiles = (SupabaseImageStorage) config.profileImageStorage(properties);
        SupabaseImageStorage documents = (SupabaseImageStorage) config.documentImageStorage(properties);

        assertEquals("pawzaar-images", pets.getBucket());
        assertEquals("pawzaar-user-profile", profiles.getBucket());
        assertEquals("pawzaar-docs", documents.getBucket());
    }

    private static void assertLocal(ImageStorage storage, String folder) {
        assertInstanceOf(LocalImageStorage.class, storage);
        // Root equals uploads/<kind> once absolutized + normalized by the storage itself.
        assertEquals(Path.of(folder).toAbsolutePath().normalize(),
                ((LocalImageStorage) storage).getRoot());
    }
}