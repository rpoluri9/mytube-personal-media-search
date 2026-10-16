package com.mytube.service.image;

import com.mytube.model.MediaItem;
import com.mytube.service.web.WebIdentification;
import com.mytube.service.web.YouTubeVideo;

import java.util.List;

/**
 * Result of one image-search attempt: the local library match (v1 behaviour)
 * plus, when a Google API key is configured, what the web knows about the
 * picture and related YouTube videos.
 */
public class ImageSearchResult {

    private final MediaItem match;
    private final int hammingDistance;
    private final boolean matched;
    private final WebIdentification webIdentification;
    private final List<YouTubeVideo> youTubeVideos;
    private final String webError;
    private final String youTubeError;

    private ImageSearchResult(MediaItem match, int hammingDistance, boolean matched,
                              WebIdentification webIdentification, List<YouTubeVideo> youTubeVideos,
                              String webError, String youTubeError) {
        this.match = match;
        this.hammingDistance = hammingDistance;
        this.matched = matched;
        this.webIdentification = webIdentification;
        this.youTubeVideos = youTubeVideos == null ? List.of() : List.copyOf(youTubeVideos);
        this.webError = webError;
        this.youTubeError = youTubeError;
    }

    public static ImageSearchResult matched(MediaItem item, int distance) {
        return new ImageSearchResult(item, distance, true, null, List.of(), null, null);
    }

    public static ImageSearchResult noMatch(int bestDistance) {
        return new ImageSearchResult(null, bestDistance, false, null, List.of(), null, null);
    }

    /** Result carrying only web data (no local library match attempted). */
    public static ImageSearchResult webOnly(WebIdentification webIdentification,
                                            List<YouTubeVideo> youTubeVideos,
                                            String webError, String youTubeError) {
        return new ImageSearchResult(null, -1, false,
                webIdentification, youTubeVideos, webError, youTubeError);
    }

    /** Merges a local library result with web identification + YouTube results. */
    public static ImageSearchResult withWeb(ImageSearchResult local,
                                            WebIdentification webIdentification,
                                            List<YouTubeVideo> youTubeVideos,
                                            String webError, String youTubeError) {
        return new ImageSearchResult(local.match, local.hammingDistance, local.matched,
                webIdentification, youTubeVideos, webError, youTubeError);
    }

    public MediaItem getMatch() { return match; }
    public int getHammingDistance() { return hammingDistance; }
    public boolean isMatched() { return matched; }
    public WebIdentification getWebIdentification() { return webIdentification; }
    public List<YouTubeVideo> getYouTubeVideos() { return youTubeVideos; }
    public String getWebError() { return webError; }
    public String getYouTubeError() { return youTubeError; }

    /** True when any web data (or web error) is present. */
    public boolean hasWebData() {
        return (webIdentification != null && !webIdentification.isEmpty())
                || !youTubeVideos.isEmpty()
                || webError != null || youTubeError != null;
    }
}
