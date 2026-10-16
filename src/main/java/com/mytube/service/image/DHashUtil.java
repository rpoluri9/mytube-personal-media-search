package com.mytube.service.image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Pure-Java implementation of the dHash (difference hash) perceptual hash.
 *
 * <p>Algorithm: shrink the image to 9x8 grayscale pixels, then for each of the
 * 64 adjacent horizontal pixel pairs record whether the left pixel is brighter
 * than the right one. The result is a 64-bit fingerprint. Two images that look
 * alike — even after resizing, recompression or slight cropping — produce hashes
 * with a small {@link #hammingDistance(long, long)}.
 *
 * <p>No AI/ML libraries are involved; this is plain pixel math.
 */
public final class DHashUtil {

    private DHashUtil() {
    }

    /** Reads image bytes (PNG/JPEG/...) into a {@link BufferedImage}, or null if unreadable. */
    public static BufferedImage readImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            return null;
        }
    }

    /** Computes the 64-bit dHash of an image. */
    public static long dhash(BufferedImage image) {
        BufferedImage small = resizeGrayscale(image, 9, 8);
        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                int left = small.getRaster().getSample(x, y, 0);
                int right = small.getRaster().getSample(x + 1, y, 0);
                if (left > right) {
                    hash |= 1L;
                }
            }
        }
        return hash;
    }

    /** Number of differing bits between two hashes; 0 means (perceptually) identical. */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    static BufferedImage resizeGrayscale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
