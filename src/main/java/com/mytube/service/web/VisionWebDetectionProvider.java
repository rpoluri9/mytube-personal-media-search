package com.mytube.service.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytube.service.image.DHashUtil;
import com.mytube.service.image.ImageMatchProvider;
import com.mytube.service.image.ImageSearchResult;
import com.mytube.service.image.InvalidImageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Identifies ANY uploaded picture using Google Cloud Vision
 * {@code images:annotate} with the {@code WEB_DETECTION} feature (the index
 * behind reverse image search) plus {@code TEXT_DETECTION} for on-screen
 * titles. Handles clean screenshots AND camera photos of screens (glare,
 * angle, surroundings): Vision understands the scene, it does not need a
 * pixel-perfect match.
 *
 * <p>Needs {@code mytube.google.api-key}. When the key is blank this provider
 * is inert (returns an empty result) so the app behaves exactly like v1 —
 * local library matching only. Any API/transport failure is converted into a
 * {@code webError} on the result instead of throwing, so the local match is
 * always still returned.
 */
@Service
public class VisionWebDetectionProvider implements ImageMatchProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final RestClient restClient;

    @Autowired
    public VisionWebDetectionProvider(
            @Value("${mytube.google.api-key:}") String apiKey,
            RestClient.Builder restClientBuilder) {
        this(apiKey, restClientBuilder.baseUrl("https://vision.googleapis.com").build());
    }

    /** Test seam: inject a RestClient backed by a stub request factory. */
    public VisionWebDetectionProvider(String apiKey, RestClient restClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = restClient;
    }

    /** True when a key is configured and web identification can run. */
    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    @Override
    public ImageSearchResult findBestMatch(byte[] imageBytes) {
        if (!isEnabled()) {
            return ImageSearchResult.webOnly(WebIdentification.EMPTY, List.of(), null, null);
        }
        if (DHashUtil.readImage(imageBytes) == null) {
            throw new InvalidImageException(
                    "The uploaded file could not be read as an image. Please upload a PNG or JPEG picture. "
                            + "If this came from an iPhone, it may be HEIC — share or re-save it as JPEG first.");
        }
        try {
            return ImageSearchResult.webOnly(identify(imageBytes), List.of(), null, null);
        } catch (WebSearchException e) {
            return ImageSearchResult.webOnly(WebIdentification.EMPTY, List.of(), e.getMessage(), null);
        }
    }

    /**
     * Runs web detection and returns what the web knows about the picture.
     *
     * @throws WebSearchException if the API call fails or the key is invalid
     */
    public WebIdentification identify(byte[] imageBytes) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String requestBody = """
                {"requests": [{"image": {"content": "%s"},
                  "features": [{"type": "WEB_DETECTION", "maxResults": 10},
                               {"type": "TEXT_DETECTION", "maxResults": 10}]}]}"""
                .formatted(base64);
        final String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/images:annotate")
                            .queryParam("key", apiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new WebSearchException(
                    "Web image search failed (" + shortCause(e) + "). "
                            + "Your local library match is shown below; "
                            + "check the API key and that 'Cloud Vision API' is enabled.",
                    e);
        }
        return parseIdentification(responseBody);
    }

    private WebIdentification parseIdentification(String responseBody) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            throw new WebSearchException("Web image search returned an unreadable response.", e);
        }
        JsonNode first = root.path("responses").path(0);
        JsonNode apiError = first.path("error");
        if (!apiError.isMissingNode()) {
            throw new WebSearchException("Web image search failed: "
                    + apiError.path("message").asText("unknown API error") + ". "
                    + "Check the API key and that 'Cloud Vision API' is enabled.");
        }
        JsonNode web = first.path("webDetection");
        if (web.isMissingNode() || web.isNull()) {
            return WebIdentification.EMPTY;
        }

        String bestGuess = null;
        JsonNode guesses = web.path("bestGuessLabels");
        if (guesses.isArray() && !guesses.isEmpty()) {
            bestGuess = guesses.path(0).path("label").asText(null);
        }

        List<WebEntity> entities = new ArrayList<>();
        for (JsonNode e : web.path("webEntities")) {
            String description = e.path("description").asText(null);
            if (description != null && !description.isBlank()) {
                entities.add(new WebEntity(description, e.path("score").asDouble(0)));
            }
            if (entities.size() >= 8) {
                break;
            }
        }

        List<String> similarImages = new ArrayList<>();
        for (JsonNode img : web.path("visuallySimilarImages")) {
            String url = img.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                similarImages.add(url);
            }
            if (similarImages.size() >= 8) {
                break;
            }
        }

        List<PageRef> pages = new ArrayList<>();
        for (JsonNode p : web.path("pagesWithMatchingImages")) {
            String url = p.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                pages.add(new PageRef(url, p.path("pageTitle").asText("")));
            }
            if (pages.size() >= 8) {
                break;
            }
        }

        return new WebIdentification(bestGuess, entities, similarImages, pages,
                extractProminentText(first));
    }

    /**
     * Pulls the prominent on-screen title out of TEXT_DETECTION word boxes.
     * Words are scored by relative box area, discounted steeply toward the
     * bottom of the frame so ad banners / UI chrome (e.g. a "Bluehost" banner
     * under the video) don't hijack the result; the keepers are joined in
     * reading order. Returns null when there is no usable text.
     */
    private String extractProminentText(JsonNode first) {
        JsonNode anns = first.path("textAnnotations");
        if (!anns.isArray() || anns.size() <= 1) {
            return null; // [0] is the full text; nothing word-level to rank
        }
        record Word(String text, double minX, double minY, double maxX, double maxY) {}
        List<Word> words = new ArrayList<>();
        double extentX = 1, extentY = 1;
        for (int i = 1; i < anns.size(); i++) {
            JsonNode a = anns.get(i);
            String d = a.path("description").asText(null);
            JsonNode verts = a.path("boundingPoly").path("vertices");
            if (d == null || d.isBlank() || !verts.isArray() || verts.isEmpty()) {
                continue;
            }
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (JsonNode v : verts) {
                double x = v.path("x").asDouble(0), y = v.path("y").asDouble(0);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            extentX = Math.max(extentX, maxX);
            extentY = Math.max(extentY, maxY);
            words.add(new Word(d, minX, minY, maxX, maxY));
        }
        if (words.isEmpty()) {
            return null;
        }
        final double ex = extentX, ey = extentY;
        record Scored(String text, double score, double yC, double xC) {}
        List<Scored> scored = new ArrayList<>();
        for (Word w : words) {
            double area = Math.max(0, w.maxX - w.minX) * Math.max(0, w.maxY - w.minY)
                    / (ex * ey);
            double yC = ((w.minY + w.maxY) / 2) / ey;
            double xC = ((w.minX + w.maxX) / 2) / ex;
            // Big text scores; bottom-of-frame text (ads, nav bars) is discounted hard.
            double score = area * Math.max(0.05, 1.5 - 1.8 * yC);
            scored.add(new Scored(w.text, score, yC, xC));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        double best = scored.get(0).score();
        if (best <= 0) {
            return null;
        }
        List<Scored> kept = scored.stream()
                .filter(s -> s.score() >= best * 0.5)
                .limit(15)
                .sorted((a, b) -> {
                    int c = Double.compare(a.yC(), b.yC());
                    return c != 0 ? c : Double.compare(a.xC(), b.xC());
                })
                .toList();
        String joined = kept.stream().map(Scored::text)
                .collect(java.util.stream.Collectors.joining(" "));
        if (joined.length() > 150) {
            joined = joined.substring(0, 150);
        }
        return joined.isBlank() ? null : joined;
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
