package com.mytube.web;

import com.mytube.model.MediaItem;
import com.mytube.service.image.ImageSearchResult;
import com.mytube.service.web.WebIdentification;
import com.mytube.service.web.YouTubeVideo;

import java.util.List;

/** JSON body returned by POST /api/search/image. */
public class ImageSearchResponse {

    private final MediaItem match;
    private final Integer hammingDistance;
    private final String message;
    private final WebIdentification webIdentification;
    private final List<YouTubeVideo> youTubeVideos;
    private final String webError;
    private final String youTubeError;

    public ImageSearchResponse(MediaItem match, Integer hammingDistance, String message,
                               WebIdentification webIdentification, List<YouTubeVideo> youTubeVideos,
                               String webError, String youTubeError) {
        this.match = match;
        this.hammingDistance = hammingDistance;
        this.message = message;
        this.webIdentification = webIdentification;
        this.youTubeVideos = youTubeVideos == null ? List.of() : List.copyOf(youTubeVideos);
        this.webError = webError;
        this.youTubeError = youTubeError;
    }

    /** Builds the full response (local match + web data) from a search result. */
    public static ImageSearchResponse fromResult(ImageSearchResult result) {
        String message;
        if (result.isMatched()) {
            message = "Matched '" + result.getMatch().getTitle() + "' (perceptual-hash distance "
                    + result.getHammingDistance() + ").";
        } else if (result.hasWebData()) {
            message = "No close match in your personal library, but the web knows this image — see below.";
        } else {
            message = "No close match found in your personal library. Try a clearer screenshot "
                    + "of one of your library thumbnails, or add a Google API key to identify any image.";
        }
        return new ImageSearchResponse(
                result.getMatch(),
                result.getHammingDistance() < 0 ? null : result.getHammingDistance(),
                message,
                result.getWebIdentification(),
                result.getYouTubeVideos(),
                result.getWebError(),
                result.getYouTubeError());
    }

    public static ImageSearchResponse matched(MediaItem item, int distance) {
        return new ImageSearchResponse(item, distance,
                "Matched '" + item.getTitle() + "' (perceptual-hash distance " + distance + ").",
                null, List.of(), null, null);
    }

    public static ImageSearchResponse noMatch(Integer bestDistance) {
        return new ImageSearchResponse(null, bestDistance,
                "No close match found in your personal library. Try a clearer screenshot "
                        + "of one of your library thumbnails.",
                null, List.of(), null, null);
    }

    public static ImageSearchResponse error(String message) {
        return new ImageSearchResponse(null, null, message, null, List.of(), null, null);
    }

    public MediaItem getMatch() { return match; }
    public Integer getHammingDistance() { return hammingDistance; }
    public String getMessage() { return message; }
    public WebIdentification getWebIdentification() { return webIdentification; }
    public List<YouTubeVideo> getYouTubeVideos() { return youTubeVideos; }
    public String getWebError() { return webError; }
    public String getYouTubeError() { return youTubeError; }
}
