package com.pawzaar.common.image;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * M1: the EXIF-orientation transform must rotate/flip in the RIGHT direction, not merely change the
 * output dimensions. These tests use a two-tone image so a wrong direction is detectable.
 */
class ImageProcessorTest {

    private static final Color LEFT = Color.RED;
    private static final Color RIGHT = Color.BLUE;

    /** A 2x1 image: left pixel RED, right pixel BLUE. */
    private static BufferedImage twoTone() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, LEFT.getRGB());
        image.setRGB(1, 0, RIGHT.getRGB());
        return image;
    }

    @Test
    void orientation1LeavesTheImageUntouched() {
        BufferedImage source = twoTone();
        assertSame(source, ImageProcessor.applyOrientation(source, 1));
    }

    @Test
    void orientation3Rotates180Degrees() {
        BufferedImage out = ImageProcessor.applyOrientation(twoTone(), 3);

        assertEquals(2, out.getWidth());
        assertEquals(1, out.getHeight());
        assertEquals(RIGHT.getRGB(), out.getRGB(0, 0));
        assertEquals(LEFT.getRGB(), out.getRGB(1, 0));
    }

    @Test
    void orientation6Rotates90Clockwise() {
        // Rotate 90 CW: the left (red) pixel ends up on top.
        BufferedImage out = ImageProcessor.applyOrientation(twoTone(), 6);

        assertEquals(1, out.getWidth());
        assertEquals(2, out.getHeight());
        assertEquals(LEFT.getRGB(), out.getRGB(0, 0));
        assertEquals(RIGHT.getRGB(), out.getRGB(0, 1));
    }

    @Test
    void orientation8Rotates90CounterClockwise() {
        // Rotate 90 CCW: the right (blue) pixel ends up on top.
        BufferedImage out = ImageProcessor.applyOrientation(twoTone(), 8);

        assertEquals(1, out.getWidth());
        assertEquals(2, out.getHeight());
        assertEquals(RIGHT.getRGB(), out.getRGB(0, 0));
        assertEquals(LEFT.getRGB(), out.getRGB(0, 1));
    }

    @Test
    void orientation2FlipsHorizontally() {
        BufferedImage out = ImageProcessor.applyOrientation(twoTone(), 2);

        assertEquals(2, out.getWidth());
        assertEquals(1, out.getHeight());
        assertEquals(RIGHT.getRGB(), out.getRGB(0, 0));
        assertEquals(LEFT.getRGB(), out.getRGB(1, 0));
    }
}
