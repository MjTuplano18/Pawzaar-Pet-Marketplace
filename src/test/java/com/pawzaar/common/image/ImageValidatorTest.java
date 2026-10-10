package com.pawzaar.common.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for image validation and normalisation (M1). Each test pins one rule: size, allowlist,
 * declared-vs-actual type, pixel caps, and metadata stripping.
 */
class ImageValidatorTest {

    private static final ImageProcessor PROCESSOR = new ImageProcessor(5000, 25_000_000);
    private final ImageValidator validator = new ImageValidator(1_000_000, PROCESSOR);

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "upload", contentType, bytes);
    }

    @Test
    void acceptsJpegAndNormalisesTheExtension() {
        ValidatedImage result = validator.validate(file("image/jpeg", jpeg(8, 8)));

        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.contentType());
    }

    @Test
    void acceptsPngAndWebp() {
        assertEquals("png", validator.validate(file("image/png", png(8, 8))).extension());
        assertEquals("webp", validator.validate(file("image/webp", webp(8, 8))).extension());
    }

    @Test
    void stripsExifFromAJpeg() {
        byte[] withExif = jpegWithExif(8, 8, 1);
        assertTrueContains(withExif, "Exif");

        byte[] stored = validator.validate(file("image/jpeg", withExif)).data();

        assertFalse(contains(stored, "Exif"),
                "the re-encoded image must not carry the EXIF block");
    }

    @Test
    void honoursTheExifOrientationSoTheImageIsNotStoredSideways() throws IOException {
        // A 16x8 image tagged orientation 6 (rotate 90 CW) must come out 8x16.
        byte[] stored = validator.validate(file("image/jpeg", jpegWithExif(16, 8, 6))).data();

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(8, decoded.getWidth());
        assertEquals(16, decoded.getHeight());
    }

    @Test
    void stripsMetadataChunksFromAWebp() {
        byte[] stored = validator.validate(file("image/webp", webpWithExif(8, 8))).data();

        assertFalse(contains(stored, "EXIF"), "the WebP EXIF chunk must be removed");
    }

    @Test
    void rejectsAnImageWithTooManyPixels() {
        ImageValidator strict = new ImageValidator(1_000_000, new ImageProcessor(10, 100));

        assertThrows(ImageDimensionsTooLargeException.class,
                () -> strict.validate(file("image/png", png(20, 20))));
    }

    @Test
    void rejectsAnImageWhoseSideExceedsTheCap() {
        ImageValidator strict = new ImageValidator(1_000_000, new ImageProcessor(10, 1_000_000));

        assertThrows(ImageDimensionsTooLargeException.class,
                () -> strict.validate(file("image/png", png(20, 5))));
    }

    @Test
    void rejectsAnUnsupportedDeclaredType() {
        assertThrows(UnsupportedImageTypeException.class,
                () -> validator.validate(file("image/gif", png(8, 8))));
    }

    @Test
    void rejectsWhenDeclaredTypeDisagreesWithTheActualBytes() {
        // Header claims PNG, but the bytes are a JPEG: a classic "renamed file" attack.
        assertThrows(UnsupportedImageTypeException.class,
                () -> validator.validate(file("image/png", jpeg(8, 8))));
    }

    @Test
    void rejectsGarbageThatOnlyClaimsToBeAnImage() {
        byte[] notAnImage = "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.US_ASCII);
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
        ImageValidator tinyLimit = new ImageValidator(10, PROCESSOR);

        assertThrows(ImageTooLargeException.class,
                () -> tinyLimit.validate(file("image/jpeg", jpeg(8, 8))));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────────

    private static byte[] jpeg(int width, int height) {
        return encode("jpeg", solidImage(width, height));
    }

    private static byte[] png(int width, int height) {
        return encode("png", solidImage(width, height));
    }

    private static BufferedImage solidImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(String format, BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A real JPEG with an EXIF APP1 segment (carrying the given orientation) inserted after SOI. */
    private static byte[] jpegWithExif(int width, int height, int orientation) {
        byte[] base = jpeg(width, height);
        byte[] app1 = exifApp1(orientation);

        byte[] out = new byte[base.length + app1.length];
        out[0] = base[0];   // SOI (FF D8)
        out[1] = base[1];
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(base, 2, out, 2 + app1.length, base.length - 2);
        return out;
    }

    /** Builds an APP1 segment: marker + length + "Exif\0\0" + a little-endian TIFF with orientation. */
    private static byte[] exifApp1(int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('I');
        tiff.write('I');
        tiff.write(0x2A);
        tiff.write(0x00);
        writeIntLE(tiff, 8);            // IFD0 starts right after this 8-byte TIFF header
        writeIntLE16(tiff, 1);          // one entry
        writeIntLE16(tiff, 0x0112);     // tag: Orientation
        writeIntLE16(tiff, 3);          // type: SHORT
        writeIntLE(tiff, 1);            // count: 1
        writeIntLE16(tiff, orientation);// value (SHORT, padded to 4 bytes)
        writeIntLE16(tiff, 0);
        writeIntLE(tiff, 0);            // no next IFD

        byte[] tiffBytes = tiff.toByteArray();
        int payloadLength = 6 + tiffBytes.length;   // "Exif\0\0" + TIFF
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        segment.write(0xFF);
        segment.write(0xE1);
        segment.write(((payloadLength + 2) >> 8) & 0xFF);
        segment.write((payloadLength + 2) & 0xFF);
        segment.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        segment.writeBytes(tiffBytes);
        return segment.toByteArray();
    }

    /** A minimal VP8L WebP (only the header our container-level code reads). */
    private static byte[] webp(int width, int height) {
        int bits = ((width - 1) & 0x3FFF) | (((height - 1) & 0x3FFF) << 14);
        byte[] payload = {0x2F, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24)};
        return riff(body("VP8L", payload));
    }

    /** A WebP carrying an EXIF chunk, for the metadata-strip test. */
    private static byte[] webpWithExif(int width, int height) {
        int bits = ((width - 1) & 0x3FFF) | (((height - 1) & 0x3FFF) << 14);
        byte[] vp8l = {0x2F, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24)};
        byte[] body = new byte[0];
        body = concat(body, chunk("VP8L", vp8l));
        body = concat(body, chunk("EXIF", "gps-data".getBytes(StandardCharsets.US_ASCII)));
        return riff(body);
    }

    private static byte[] riff(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeIntLE(out, 4 + body.length);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static byte[] body(String fourCC, byte[] payload) {
        return chunk(fourCC, payload);
    }

    private static byte[] chunk(String fourCC, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourCC.getBytes(StandardCharsets.US_ASCII));
        writeIntLE(out, payload.length);
        out.writeBytes(payload);
        if ((payload.length & 1) == 1) {
            out.write(0);   // RIFF chunks are padded to an even length
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeIntLE16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    // ── byte-search helpers ──────────────────────────────────────────────────────

    private static void assertTrueContains(byte[] haystack, String needle) {
        assertFalse(!contains(haystack, needle), "expected to find " + needle);
    }

    private static boolean contains(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + n.length <= haystack.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
