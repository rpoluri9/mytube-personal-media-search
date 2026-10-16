package com.mytube.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Searches real YouTube videos via the YouTube Data API v3 {@code search}
 * endpoint ({@code part=snippet}, {@code type=video}). Uses the same
 * {@code mytube.google.api-key} as Vision (one key covers both APIs).
 *
 * <p>Failures throw {@link WebSearchException} with a clear message — callers
 * are expected to catch it and degrade gracefully.
 */
@Service
public class YouTubeSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final int defaultMaxResults;
    private final RestClient restClient;

    @Autowired
    public YouTubeSearchService(
            @Value("${mytube.google.api-key:}") String apiKey,
            @Value("${mytube.youtube.max-results:5}") int defaultMaxResults,
            RestClient.Builder restClientBuilder) {
        this(apiKey, defaultMaxResults,
                restClientBuilder.baseUrl("https://www.googleapis.com").build());
    }

    /** Test seam: inject a RestClient backed by a stub request factory. */
    public YouTubeSearchService(String apiKey, int defaultMaxResults, RestClient restClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.defaultMaxResults = Math.min(50, Math.max(1, defaultMaxResults));
        this.restClient = restClient;
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public List<YouTubeVideo> searchVideos(String query) {
        return searchVideos(query, defaultMaxResults);
    }

    /**
     * @param query what to search YouTube for (e.g. the image's best-guess label)
     * @param max   1..50 results
     * @throws WebSearchException if the API call fails
     */
    public List<YouTubeVideo> searchVideos(String query, int max) {
        if (!isEnabled()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        final String responseBody;
        try {
            responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("q", query)
                            .queryParam("maxResults", Math.min(50, Math.max(1, max)))
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new WebSearchException(
                    "YouTube search failed (" + shortCause(e) + "). "
                            + "Check the API key and that 'YouTube Data API v3' is enabled.",
                    e);
        }
        return parseVideos(responseBody);
    }

    /**
     * Fetches full details for known video IDs ({@code videos.list},
     * {@code part=snippet}) — used to resolve the exact original video when
     * Vision's matching pages link to a YouTube watch/Shorts URL. Costs only
     * 1 quota unit per call.
     *
     * @throws WebSearchException if the API call fails
     */
    public List<YouTubeVideo> getVideoDetails(List<String> videoIds) {
        if (!isEnabled() || videoIds == null || videoIds.isEmpty()) {
            return List.of();
        }
        String ids = String.join(",", videoIds.stream().limit(10).toList());
        final String responseBody;
        try {
            responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/youtube/v3/videos")
                            .queryParam("part", "snippet")
                            .queryParam("id", ids)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new WebSearchException(
                    "YouTube video lookup failed (" + shortCause(e) + ").", e);
        }
        return parseVideos(responseBody);
    }

    /**
     * Pulls YouTube video IDs out of web page URLs (watch, Shorts and
     * youtu.be links), e.g. from Vision's {@code pagesWithMatchingImages} —
     * that's how an uploaded screenshot resolves back to its original video
     * and channel.
     */
    public static List<String> extractYouTubeVideoIds(List<PageRef> pages) {
        List<String> ids = new ArrayList<>();
        if (pages == null) {
            return ids;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:youtube\\.com/(?:watch\\?[^#\\s]*v=|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})");
        for (PageRef page : pages) {
            if (page == null || page.url() == null) {
                continue;
            }
            java.util.regex.Matcher m = p.matcher(page.url());
            while (m.find() && ids.size() < 3) {
                if (!ids.contains(m.group(1))) {
                    ids.add(m.group(1));
                }
            }
        }
        return ids;
    }

    private List<YouTubeVideo> parseVideos(String responseBody) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            throw new WebSearchException("YouTube search returned an unreadable response.", e);
        }
        JsonNode apiError = root.path("error");
        if (!apiError.isMissingNode()) {
            throw new WebSearchException("YouTube search failed: "
                    + apiError.path("message").asText("unknown API error") + ".");
        }
        List<YouTubeVideo> videos = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            // search.list: id is {"videoId": "..."}; videos.list: id is a plain string
            JsonNode idNode = item.path("id");
            String videoId = idNode.isTextual()
                    ? idNode.asText(null)
                    : idNode.path("videoId").asText(null);
            JsonNode snippet = item.path("snippet");
            String title = snippet.path("title").asText(null);
            if (videoId == null || title == null) {
                continue;
            }
            String thumb = snippet.path("thumbnails").path("medium").path("url").asText(null);
            if (thumb == null) {
                thumb = snippet.path("thumbnails").path("default").path("url").asText(null);
            }
            videos.add(YouTubeVideo.of(
                    videoId,
                    title,
                    snippet.path("channelTitle").asText(""),
                    snippet.path("channelId").asText(""),
                    snippet.path("description").asText(""),
                    thumb));
        }
        return videos;
    }

    private static String shortCause(RestClientException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        int cut = Math.min(msg.length(), 160);
        return msg.substring(0, cut).replaceAll("\\s+", " ");
    }
}
