package com.pawzaar.common.image;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an upload is an acceptable image, and normalises it for storage.
 *
 * <p>Three checks, each guarding a different failure:
 * <ol>
 *   <li><b>Size</b> - a cheap early rejection before we read bytes into memory.</li>
 *   <li><b>Declared content type</b> - the allowlist (JPEG/PNG/WebP).</li>
 *   <li><b>Magic bytes</b> - the first bytes of the file must actually be that format. The
 *       {@code Content-Type} header is client-supplied and trivially spoofed, so it is a hint, not
 *       proof; sniffing the bytes is what stops someone uploading an executable renamed
 *       {@code cute.png}. The declared type and the sniffed type must also agree.</li>
 * </ol>
 */
public class ImageValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final long maxBytes;

    public ImageValidator(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /** Validates the file and returns the bytes, extension and verified MIME type to store. */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("An image file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new ImageTooLargeException(file.getSize(), maxBytes);
        }

        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(declared)) {
            throw new UnsupportedImageTypeException(declared);
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("The uploaded file could not be read");
        }

        String kind = sniff(data);
        if (kind == null) {
            throw new InvalidImageException("The file is not a valid JPEG, PNG or WebP image");
        }
        String actualType = contentTypeFor(kind);
        if (!actualType.equals(declared)) {
            // e.g. header says PNG but the bytes are a JPEG (or something else entirely).
            throw new UnsupportedImageTypeException(declared);
        }
        return new ValidatedImage(data, extensionFor(kind), actualType);
    }

    /** Reads the leading "magic" bytes and returns jpeg/png/webp, or null if none match. */
    private static String sniff(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static String contentTypeFor(String kind) {
        return switch (kind) {
            case "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new IllegalArgumentException("Unknown image kind: " + kind);
        };
    }

    private static String extensionFor(String kind) {
        return switch (kind) {
            case "jpeg" -> "jpg";
            case "png" -> "png";
            case "webp" -> "webp";
            default -> throw new IllegalArgumentException("Unknown image kind: " + kind);
        };
    }
}
