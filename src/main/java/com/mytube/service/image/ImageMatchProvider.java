package com.mytube.service.image;

/**
 * Strategy interface for "search by image".
 *
 * <p>The default implementation ({@link LocalHashMatchProvider}) matches the
 * uploaded picture against this personal library using perceptual hashing. To
 * search the whole web instead, implement this interface with a reverse-image
 * API client (e.g. SerpApi) and register it as the {@code @Primary} bean —
 * no controller changes are needed. See README "How to extend".
 */
public interface ImageMatchProvider {

    /**
     * Finds the best library match for the uploaded image bytes.
     *
     * @param imageBytes raw bytes of the uploaded file
     * @return the match result; never null
     * @throws InvalidImageException if the bytes are not a readable image
     */
    ImageSearchResult findBestMatch(byte[] imageBytes);
}
