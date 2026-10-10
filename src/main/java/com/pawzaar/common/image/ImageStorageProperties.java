package com.pawzaar.common.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Settings for image storage, bound from {@code pawzaar.storage.*} (overridable by environment
 * variables on the host).
 *
 * <p>{@link Type} picks the {@link ImageStorage} implementation:
 * <ul>
 *   <li>{@code LOCAL} - writes files to {@link #root}. The default, so tests and local dev need no
 *       external service and no credentials.</li>
 *   <li>{@code SUPABASE} - uploads to a Supabase Storage bucket over its REST API, configured by the
 *       nested {@link Supabase} block.</li>
 * </ul>
 *
 * <p>Defaulting to {@code LOCAL} is deliberate: a fresh clone and the whole test suite must run with
 * no cloud account. Switching to Supabase is one environment variable
 * ({@code PAWZAAR_STORAGE_TYPE=supabase}) plus the credentials below.
 */
@ConfigurationProperties(prefix = "pawzaar.storage")
public class ImageStorageProperties {

    /** Which storage backend to build. */
    public enum Type {
        LOCAL,
        SUPABASE
    }

    private Type type = Type.LOCAL;

    /** Directory that {@code LOCAL} images are written to. Relative paths resolve from the working dir. */
    private Path root = Path.of("uploads");

    /** Largest accepted image, in bytes. Defaults to 5 MiB. */
    private long maxImageBytes = 5L * 1024 * 1024;

    /** Supabase Storage settings; only read when {@link #type} is {@link Type#SUPABASE}. */
    private final Supabase supabase = new Supabase();

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public Supabase getSupabase() {
        return supabase;
    }

    /**
     * Supabase project connection details. The {@code serviceKey} is a server-side secret: it must
     * never be shipped to a browser or committed. Every value here comes from environment variables.
     */
    public static class Supabase {

        /** Project base URL, e.g. {@code https://abcdefgh.supabase.co} (no trailing slash). */
        private String url = "";

        /** Bucket that holds the images. Keep it private; the API proxies reads. */
        private String bucket = "pawzaar-images";

        /** Service-role key used as the bearer token. Bypasses row-level security - server only. */
        private String serviceKey = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }
}
