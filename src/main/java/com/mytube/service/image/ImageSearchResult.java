package com.mytube.service.image;

import com.mytube.model.MediaItem;

/** Result of one image-search attempt. */
public class ImageSearchResult {

    private final MediaItem match;
    private final int hammingDistance;
    private final boolean matched;

    private ImageSearchResult(MediaItem match, int hammingDistance, boolean matched) {
        this.match = match;
        this.hammingDistance = hammingDistance;
        this.matched = matched;
    }

    public static ImageSearchResult matched(MediaItem item, int distance) {
        return new ImageSearchResult(item, distance, true);
    }

    public static ImageSearchResult noMatch(int bestDistance) {
        return new ImageSearchResult(null, bestDistance, false);
    }

    public MediaItem getMatch() { return match; }
    public int getHammingDistance() { return hammingDistance; }
    public boolean isMatched() { return matched; }
}
