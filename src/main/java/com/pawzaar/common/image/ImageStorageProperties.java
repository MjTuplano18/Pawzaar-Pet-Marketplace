package com.pawzaar.common.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Settings for image storage, bound from {@code pawzaar.storage.*} (overridable by environment
 * variables on the host).
 */
@ConfigurationProperties(prefix = "pawzaar.storage")
public class ImageStorageProperties {

    /** Directory that uploaded images are written to. Relative paths are resolved from the working dir. */
    private Path root = Path.of("uploads");

    /** Largest accepted image, in bytes. Defaults to 5 MiB. */
    private long maxImageBytes = 5L * 1024 * 1024;

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
}
