package com.pawzaar.config;

import com.pawzaar.common.image.ImageStorage;
import com.pawzaar.common.image.ImageStorageProperties;
import com.pawzaar.common.image.ImageValidator;
import com.pawzaar.common.image.LocalImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the image storage beans. The interface ({@link ImageStorage}) is what the service depends
 * on; {@link LocalImageStorage} is just the implementation chosen here. Point this at an S3-backed
 * implementation one day and nothing else changes.
 */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(ImageStorageProperties properties) {
        return new LocalImageStorage(properties.getRoot());
    }

    @Bean
    public ImageValidator imageValidator(ImageStorageProperties properties) {
        return new ImageValidator(properties.getMaxImageBytes());
    }
}
