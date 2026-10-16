package com.mytube;

import com.mytube.service.ThumbnailService;
import com.mytube.service.image.DHashUtil;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure-Java perceptual hash (dHash).
 */
class DHashUtilTest {

    private final ThumbnailService thumbnails;

    DHashUtilTest() throws Exception {
        thumbnails = new ThumbnailService(System.getProperty("java.io.tmpdir") + "/mytube-test-thumbs");
    }

    @Test
    void identicalImages_hashToZeroDistance() {
        BufferedImage a = thumbnails.render("RRR", "MOVIE");
        BufferedImage b = thumbnails.render("RRR", "MOVIE");
        assertEquals(DHashUtil.dhash(a), DHashUtil.dhash(b));
        assertEquals(0, DHashUtil.hammingDistance(DHashUtil.dhash(a), DHashUtil.dhash(b)));
    }

    @Test
    void hammingDistance_knownValues() {
        assertEquals(0, DHashUtil.hammingDistance(0L, 0L));
        assertEquals(1, DHashUtil.hammingDistance(0b101L, 0b100L));
        assertEquals(64, DHashUtil.hammingDistance(0L, -1L)); // -1L = all 64 bits set
        assertEquals(2, DHashUtil.hammingDistance(0b1100L, 0b1010L));
    }

    @Test
    void differentImages_largeDistance() {
        long h1 = DHashUtil.dhash(thumbnails.render("RRR", "MOVIE"));
        long h2 = DHashUtil.dhash(thumbnails.render("The Blue Jackal", "STORY"));
        int distance = DHashUtil.hammingDistance(h1, h2);
        assertTrue(distance > 10,
                "Different pictures should be far apart, but distance was " + distance);
    }

    @Test
    void scaledCopy_smallDistance() {
        // A "screenshot-like" resized copy must still match (distance <= threshold 10).
        BufferedImage original = thumbnails.render("Naatu Naatu", "SONG");
        BufferedImage small = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, 160, 90, null);
        g.dispose();

        int distance = DHashUtil.hammingDistance(
                DHashUtil.dhash(original), DHashUtil.dhash(small));
        assertTrue(distance <= 8,
                "Resized copy should stay close, but distance was " + distance);
    }

    @Test
    void dhash_isDeterministic() {
        long first = DHashUtil.dhash(thumbnails.render("Dune: Part Two", "MOVIE"));
        long second = DHashUtil.dhash(thumbnails.render("Dune: Part Two", "MOVIE"));
        assertEquals(first, second);
    }

    @Test
    void readImage_returnsNullForGarbage() {
        assertNull(DHashUtil.readImage("this is definitely not an image".getBytes()));
    }

    @Test
    void readImage_returnsNullForEmpty() {
        assertNull(DHashUtil.readImage(new byte[0]));
        assertNull(DHashUtil.readImage(null));
    }
}
