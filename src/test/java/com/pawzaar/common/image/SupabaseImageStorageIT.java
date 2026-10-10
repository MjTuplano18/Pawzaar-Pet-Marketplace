package com.pawzaar.common.image;

import com.pawzaar.config.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.Resource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Opt-in integration test that exercises {@link SupabaseImageStorage} against the <em>real</em>
 * Supabase bucket.
 *
 * <p>It is deliberately NOT named {@code *Test}: Surefire (which runs {@code mvn test}) only picks up
 * {@code *Test}/{@code *Tests}, so the everyday suite stays fast, offline and deterministic. Run this
 * one on demand, with credentials present:
 *
 * <pre>
 *   # PowerShell, after filling in .env.dev:
 *   Get-Content .env.dev | ForEach-Object { if ($_ -match '^[A-Za-z_]\w*=') { $p=$_.Split('=',2); [Environment]::SetEnvironmentVariable($p[0].Trim(),$p[1].Trim()) } }
 *   .\mvnw.cmd test "-Dtest=SupabaseImageStorageIT"
 * </pre>
 *
 * <p>The {@code @EnabledIfEnvironmentVariable} guard means it self-skips when credentials are absent.
 */
@EnabledIfEnvironmentVariable(named = "SUPABASE_SERVICE_KEY", matches = ".+")
class SupabaseImageStorageIT {

    /** A real 1x1 PNG, so the bytes are a valid image end to end. */
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Test
    void storeLoadDeleteRoundTripAgainstRealBucket() throws Exception {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setType(ImageStorageProperties.Type.SUPABASE);
        properties.getSupabase().setUrl(System.getenv("SUPABASE_URL"));
        properties.getSupabase().setBucket(System.getenv("SUPABASE_BUCKET"));
        properties.getSupabase().setServiceKey(System.getenv("SUPABASE_SERVICE_KEY"));

        ImageStorage storage = new StorageConfig().petImageStorage(properties);

        String key = storage.store(ONE_PIXEL_PNG, "png");
        try {
            Resource resource = storage.load(key);
            assertArrayEquals(ONE_PIXEL_PNG, resource.getContentAsByteArray());
        } finally {
            // Deleting removes the object at the origin. We deliberately do NOT assert that a
            // subsequent load fails: Supabase serves object GETs through Cloudflare, which can keep
            // returning a cached copy for a short while after the delete (CF-Cache-Status: HIT).
            // That is harmless for the app - the API resolves the database row first and 404s a
            // deleted image before it would ever touch storage.
            storage.delete(key);
        }
    }
}
