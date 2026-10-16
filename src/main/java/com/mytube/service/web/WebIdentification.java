package com.mytube.service.web;

import java.util.Collections;
import java.util.List;

/**
 * What the web knows about an uploaded picture, from Cloud Vision: the
 * WEB_DETECTION best guess, entities deduced from similar images (the
 * "history / what this is"), visually similar images, pages containing
 * matching images, plus prominent on-screen text from TEXT_DETECTION
 * (ranked by size and position so bottom ad banners don't win).
 *
 * <p>Degrades gracefully: when Vision is unsure there may be no best-guess
 * label, but similar images / on-screen text can still be shown instead of
 * nothing.
 */
public class WebIdentification {

    private final String bestGuessLabel;
    private final List<WebEntity> entities;
    private final List<String> visuallySimilarImageUrls;
    private final List<PageRef> pagesWithMatchingImages;
    private final String onScreenText;

    public static final WebIdentification EMPTY =
            new WebIdentification(null, List.of(), List.of(), List.of(), null);

    public WebIdentification(String bestGuessLabel,
                             List<WebEntity> entities,
                             List<String> visuallySimilarImageUrls,
                             List<PageRef> pagesWithMatchingImages,
                             String onScreenText) {
        this.bestGuessLabel = bestGuessLabel;
        this.entities = entities == null ? List.of() : List.copyOf(entities);
        this.visuallySimilarImageUrls = visuallySimilarImageUrls == null
                ? List.of() : List.copyOf(visuallySimilarImageUrls);
        this.pagesWithMatchingImages = pagesWithMatchingImages == null
                ? List.of() : List.copyOf(pagesWithMatchingImages);
        this.onScreenText = onScreenText;
    }

    /** True when Vision returned nothing usable at all. */
    public boolean isEmpty() {
        return (bestGuessLabel == null || bestGuessLabel.isBlank())
                && entities.isEmpty()
                && visuallySimilarImageUrls.isEmpty()
                && pagesWithMatchingImages.isEmpty()
                && (onScreenText == null || onScreenText.isBlank());
    }

    public String getBestGuessLabel() { return bestGuessLabel; }
    public List<WebEntity> getEntities() { return Collections.unmodifiableList(entities); }
    public List<String> getVisuallySimilarImageUrls() {
        return Collections.unmodifiableList(visuallySimilarImageUrls);
    }
    public List<PageRef> getPagesWithMatchingImages() {
        return Collections.unmodifiableList(pagesWithMatchingImages);
    }
    public String getOnScreenText() { return onScreenText; }
}
