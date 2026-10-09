package com.pawzaar.pet.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for image validation. Each test pins one rule: size, allowlist, and - the important
 * one - that the declared content type must match the file's actual magic bytes.
 */
class ImageValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] PNG  = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private final ImageValidator validator = new ImageValidator(1024);

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "upload", contentType, bytes);
    }

    @Test
    void acceptsJpegAndNormalisesTheExtension() {
        ValidatedImage result = validator.validate(file("image/jpeg", JPEG));

        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.contentType());
    }

    @Test
    void acceptsPngAndWebp() {
        assertEquals("png", validator.validate(file("image/png", PNG)).extension());
        assertEquals("webp", validator.validate(file("image/webp", WEBP)).extension());
    }

    @Test
    void rejectsAnUnsupportedDeclaredType() {
        assertThrows(UnsupportedImageTypeException.class,
                () -> validator.validate(file("image/gif", PNG)));
    }

    @Test
    void rejectsWhenDeclaredTypeDisagreesWithTheActualBytes() {
        // Header claims PNG, but the bytes are a JPEG: a classic "renamed file" attack.
        assertThrows(UnsupportedImageTypeException.class,
                () -> validator.validate(file("image/png", JPEG)));
    }

    @Test
    void rejectsGarbageThatOnlyClaimsToBeAnImage() {
        byte[] notAnImage = "#!/bin/sh\nrm -rf /".getBytes();
        assertThrows(InvalidImageException.class,
                () -> validator.validate(file("image/png", notAnImage)));
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThrows(InvalidImageException.class,
                () -> validator.validate(file("image/png", new byte[0])));
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        ImageValidator tinyLimit = new ImageValidator(10);
        byte[] tooBig = new byte[100];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);

        assertThrows(ImageTooLargeException.class,
                () -> tinyLimit.validate(file("image/jpeg", tooBig)));
    }
}
