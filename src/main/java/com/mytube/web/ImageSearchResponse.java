package com.mytube.web;

import com.mytube.model.MediaItem;
import java.util.Map;

/** JSON body returned by POST /api/search/image. */
public class ImageSearchResponse {

    private final MediaItem match;
    private final Integer hammingDistance;
    private final String message;

    public ImageSearchResponse(MediaItem match, Integer hammingDistance, String message) {
        this.match = match;
        this.hammingDistance = hammingDistance;
        this.message = message;
    }

    public static ImageSearchResponse matched(MediaItem item, int distance) {
        return new ImageSearchResponse(item, distance,
                "Matched '" + item.getTitle() + "' (perceptual-hash distance " + distance + ").");
    }

    public static ImageSearchResponse noMatch(Integer bestDistance) {
        return new ImageSearchResponse(null, bestDistance,
                "No close match found in your personal library. Try a clearer screenshot "
                        + "of one of your library thumbnails.");
    }

    public static ImageSearchResponse error(String message) {
        return new ImageSearchResponse(null, null, message);
    }

    public MediaItem getMatch() { return match; }
    public Integer getHammingDistance() { return hammingDistance; }
    public String getMessage() { return message; }

    /** Convenience for tests: expose as a map. */
    public Map<String, Object> asMap() {
        return Map.of("match", match, "hammingDistance", hammingDistance, "message", message);
    }
}
