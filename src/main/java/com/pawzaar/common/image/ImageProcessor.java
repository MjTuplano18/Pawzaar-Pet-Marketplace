package com.pawzaar.common.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Normalises an uploaded image before it is stored (M1): checks its PIXEL dimensions and STRIPS
 * metadata.
 *
 * <p>Why this matters:
 * <ul>
 *   <li><b>Privacy.</b> A phone photo carries EXIF, often including GPS coordinates. Re-encoding
 *       drops ALL metadata, so a seller's home location is never published with their listing.</li>
 *   <li><b>Decompression bombs.</b> A few KB of PNG can decode to gigabytes of pixels and exhaust
 *       the heap. We read the dimensions from the header FIRST and reject oversized images before
 *       any full decode.</li>
 * </ul>
 *
 * <p>JPEG and PNG are re-encoded through ImageIO (which also lets us honour the EXIF orientation so
 * photos are not sideways once the tag is gone). The JDK has no WebP codec, so a WebP is handled at
 * the container level: its dimensions are read from the VP8/VP8L/VP8X header and its metadata
 * chunks (EXIF/XMP) are removed by rewriting the RIFF container.
 */
public class ImageProcessor {

    private final int maxDimension;
    private final long maxPixels;

    public ImageProcessor(int maxDimension, long maxPixels) {
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
    }

    /** Returns the exact bytes to store: dimensions checked, metadata removed. */
    public byte[] normalise(byte[] data, String kind) {
        return switch (kind) {
            case "jpeg" -> normaliseRaster(data, "jpeg", true);
            case "png" -> normaliseRaster(data, "png", false);
            case "webp" -> normaliseWebp(data);
            default -> throw new IllegalArgumentException("Unknown image kind: " + kind);
        };
    }

    // ── JPEG / PNG ───────────────────────────────────────────────────────────────

    private byte[] normaliseRaster(byte[] data, String format, boolean jpeg) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidImageException("The image could not be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Header-only read: safe against decompression bombs, since the pixels are not
                // decoded until reader.read() below - which we only reach for an acceptable size.
                checkDimensions(reader.getWidth(0), reader.getHeight(0));

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidImageException("The image could not be decoded");
                }
                if (jpeg) {
                    // Apply the EXIF orientation, otherwise a rotated phone photo becomes sideways
                    // the moment we strip the tag that recorded the rotation.
                    image = applyOrientation(image, exifOrientation(data));
                    image = toRgb(image);
                }
                return encode(image, format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("The image could not be read");
        }
    }

    /**
     * Re-encodes through ImageIO. Crucially it passes a {@code null} metadata object, so no EXIF,
     * GPS, XMP, IPTC or comment data is carried over - the rebuilt image is pixels only.
     */
    private static byte[] encode(BufferedImage image, String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new InvalidImageException("No encoder available for " + format);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(format) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.9f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new InvalidImageException("The image could not be processed");
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** JPEG cannot store transparency; flatten any alpha onto white so the write never fails. */
    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    /**
     * Applies the EXIF orientation (1-8) by drawing the source through an affine transform. The
     * transforms are the standard EXIF mappings; the drawing context handles the pixel-centre maths.
     */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swapped = orientation >= 5;
        int outW = swapped ? h : w;
        int outH = swapped ? w : h;
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(outW, outH, type);

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }               // flip horizontal
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }            // rotate 180
            case 4 -> { t.translate(0, h); t.scale(1, -1); }               // flip vertical
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }           // transpose
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }        // rotate 90 CW
            case 7 -> { t.translate(h, w); t.rotate(Math.PI / 2); t.scale(-1, 1); }  // transverse
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }    // rotate 90 CCW
            default -> { }
        }

        Graphics2D g = dst.createGraphics();
        g.setTransform(t);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    // ── WebP (container-level, no decoder) ───────────────────────────────────────

    private byte[] normaliseWebp(byte[] data) {
        int[] dims = webpDimensions(data);
        checkDimensions(dims[0], dims[1]);
        return stripWebpMetadata(data);
    }

    /** Removes the EXIF and XMP chunks by rebuilding the RIFF container around the remaining ones. */
    private static byte[] stripWebpMetadata(byte[] data) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        int offset = 12;   // past "RIFF" + size + "WEBP"
        while (offset + 8 <= data.length) {
            int size = readIntLE(data, offset + 4);
            int total = 8 + size + (size & 1);   // chunks are padded to an even length
            if (size < 0 || offset + total > data.length) {
                break;
            }
            String fourCC = new String(data, offset, 4, StandardCharsets.US_ASCII);
            if (!fourCC.equals("EXIF") && !fourCC.equals("XMP ")) {
                if (fourCC.equals("VP8X")) {
                    // Copy the VP8X chunk with the EXIF/XMP presence bits cleared, so the flags do
                    // not advertise metadata that is no longer there.
                    chunks.write(data, offset, 8);
                    chunks.write(data[offset + 8] & ~0x0C);   // clear EXIF (0x08) and XMP (0x04)
                    chunks.write(data, offset + 9, total - 9);
                } else {
                    chunks.write(data, offset, total);
                }
            }
            offset += total;
        }

        byte[] body = chunks.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeIntLE(out, 4 + body.length);   // "WEBP" + chunks
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static int[] webpDimensions(byte[] d) {
        int offset = 12;
        while (offset + 8 <= d.length) {
            int size = readIntLE(d, offset + 4);
            int payload = offset + 8;
            if (size < 0 || payload + size > d.length) {
                break;
            }
            String fourCC = new String(d, offset, 4, StandardCharsets.US_ASCII);
            switch (fourCC) {
                case "VP8X":
                    // Extended format: 1 byte flags, 3 reserved, then 24-bit canvas width/height - 1.
                    return new int[]{readIntLE24(d, payload + 4) + 1, readIntLE24(d, payload + 7) + 1};
                case "VP8 ":
                    // Lossy bitstream: 3-byte frame tag, 3-byte sync code, then 16-bit width/height.
                    return new int[]{
                            (readIntLE16(d, payload + 6)) & 0x3FFF,
                            (readIntLE16(d, payload + 8)) & 0x3FFF};
                case "VP8L":
                    // Lossless bitstream: 1 signature byte then 14-bit (width-1) and (height-1).
                    int bits = readIntLE(d, payload + 1);
                    return new int[]{(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
                default:
                    offset = payload + size + (size & 1);
            }
        }
        throw new InvalidImageException("The WebP image has no readable dimensions");
    }

    // ── Dimension policy ─────────────────────────────────────────────────────────

    private void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new InvalidImageException("The image has invalid dimensions");
        }
        if (width > maxDimension || height > maxDimension) {
            throw new ImageDimensionsTooLargeException(width, height,
                    "each side must be at most " + maxDimension + "px");
        }
        if ((long) width * height > maxPixels) {
            throw new ImageDimensionsTooLargeException(width, height,
                    "the image must have at most " + maxPixels + " pixels");
        }
    }

    // ── EXIF orientation (JPEG) ──────────────────────────────────────────────────

    /** Returns the EXIF orientation (1-8), or 1 when absent/unreadable. */
    private static int exifOrientation(byte[] d) {
        int i = 2;   // skip SOI
        while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xD8) {
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return 1;   // reached the scan / end without finding EXIF
            }
            int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
            if (len < 2) {
                return 1;
            }
            if (marker == 0xE1) {
                int p = i + 4;
                if (p + 6 <= d.length
                        && d[p] == 'E' && d[p + 1] == 'x' && d[p + 2] == 'i' && d[p + 3] == 'f'
                        && d[p + 4] == 0 && d[p + 5] == 0) {
                    int orientation = orientationFromTiff(d, p + 6);
                    if (orientation != 0) {
                        return orientation;
                    }
                }
            }
            i += 2 + len;
        }
        return 1;
    }

    /** Reads tag 0x0112 (Orientation) from IFD0 of a TIFF/EXIF block; 0 if absent. */
    private static int orientationFromTiff(byte[] d, int tiff) {
        if (tiff + 8 > d.length) {
            return 0;
        }
        boolean little;
        if (d[tiff] == 'I' && d[tiff + 1] == 'I') {
            little = true;
        } else if (d[tiff] == 'M' && d[tiff + 1] == 'M') {
            little = false;
        } else {
            return 0;
        }
        int ifd = tiff + readInt32(d, tiff + 4, little);
        if (ifd + 2 > d.length) {
            return 0;
        }
        int count = readInt16(d, ifd, little);
        for (int k = 0; k < count; k++) {
            int entry = ifd + 2 + k * 12;
            if (entry + 12 > d.length) {
                return 0;
            }
            if (readInt16(d, entry, little) == 0x0112 && readInt16(d, entry + 2, little) == 3) {
                return readInt16(d, entry + 8, little);   // SHORT value stored in the value field
            }
        }
        return 0;
    }

    // ── little/big-endian readers ────────────────────────────────────────────────

    private static int readInt16(byte[] d, int o, boolean little) {
        return little
                ? (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8)
                : ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    private static int readInt32(byte[] d, int o, boolean little) {
        return little
                ? (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24)
                : ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    private static int readIntLE(byte[] d, int o) {
        return readInt32(d, o, true);
    }

    private static int readIntLE16(byte[] d, int o) {
        return readInt16(d, o, true);
    }

    private static int readIntLE24(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
