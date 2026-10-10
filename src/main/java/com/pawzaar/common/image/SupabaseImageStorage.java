package com.pawzaar.common.image;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.UUID;

/**
 * Stores images in a Supabase Storage bucket over its REST API.
 *
 * <p>Why REST and not an S3 SDK: Supabase exposes the object API directly
 * ({@code /storage/v1/object/{bucket}/{path}}), so a plain {@link RestClient} - already part of the
 * web starter - is enough. No extra dependency, and the key idea from {@link ImageStorage} still
 * holds: callers only ever see an opaque key.
 *
 * <p>Two rules keep this safe and portable:
 * <ul>
 *   <li><b>Server-generated keys.</b> A random UUID plus a validated extension, exactly like
 *       {@link LocalImageStorage}. The bucket path can never contain {@code ..} or user input.</li>
 *   <li><b>Failures become {@link ImageStorageException}.</b> The controller layer maps that to a
 *       502, so a Supabase outage/expired key surfaces as a clean error, not a leaked stack
 *       trace or a half-written row. The client itself carries connect and read timeouts (H8) so a
 *       hung Supabase cannot block a request thread forever.</li>
 * </ul>
 *
 * <p>The HTTP client (base URL + auth headers) is built in {@code StorageConfig} so this class stays
 * a thin, easily-testable wrapper around one {@link RestClient}.
 */
public class SupabaseImageStorage implements ImageStorage {

    /** Supabase's object API path. Braces are template variables filled in per call. */
    private static final String OBJECT_PATH = "/storage/v1/object/{bucket}/{key}";

    private final RestClient client;
    private final String bucket;

    public SupabaseImageStorage(RestClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    /** The bucket this store writes to (e.g. {@code pawzaar-images}). Exposed for tests/debugging. */
    public String getBucket() {
        return bucket;
    }

    @Override
    public String store(byte[] data, String extension) {
        String key = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            client.post()
                    .uri(OBJECT_PATH, bucket, key)
                    // Supabase treats a repeat upload of the same path as an error unless upsert is set.
                    // Keys are random, so a collision is essentially impossible, but upsert keeps retries safe.
                    .header("x-upsert", "true")
                    .contentType(mediaTypeFor(extension))
                    .body(data)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ImageStorageException("Could not store image in Supabase", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        try {
            byte[] bytes = client.get()
                    .uri(OBJECT_PATH, bucket, key)
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null) {
                throw new ImageStorageException("Empty image body for key " + key);
            }
            // ByteArrayResource (not a stream) so the caller can read the length and re-read the bytes.
            return new ByteArrayResource(bytes);
        } catch (RestClientException e) {
            throw new ImageStorageException("Could not load image from Supabase", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.delete()
                    .uri(OBJECT_PATH, bucket, key)
                    .retrieve()
                    // Deleting something already gone is a no-op per the interface contract: swallow 404.
                    .onStatus(status -> status.value() == 404, (request, response) -> { })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ImageStorageException("Could not delete image from Supabase", e);
        }
    }

    /** Supabase stores the object's content type, so map our validated extensions back to MIME types. */
    private static MediaType mediaTypeFor(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
