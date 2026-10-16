package com.mytube.service.image;

import com.mytube.model.MediaItem;
import com.mytube.repo.MediaItemRepository;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Matches an uploaded picture against the personal media library using the
 * perceptual dHash: the library item with the smallest Hamming distance wins,
 * and it counts as a match when the distance is within {@link #MATCH_THRESHOLD}.
 */
@Service
public class LocalHashMatchProvider implements ImageMatchProvider {

    /** Max Hamming distance that still counts as "the same picture". */
    public static final int MATCH_THRESHOLD = 10;

    private final MediaItemRepository repository;

    public LocalHashMatchProvider(MediaItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public ImageSearchResult findBestMatch(byte[] imageBytes) {
        BufferedImage image = DHashUtil.readImage(imageBytes);
        if (image == null) {
            throw new InvalidImageException(
                    "The uploaded file could not be read as an image. Please upload a PNG or JPEG picture. "
                            + "If this came from an iPhone, it may be HEIC — share or re-save it as JPEG first.");
        }
        long hash = DHashUtil.dhash(image);

        MediaItem best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MediaItem item : repository.findAll()) {
            if (item.getImageHash() == null) {
                continue;
            }
            int distance = DHashUtil.hammingDistance(hash, item.getImageHash());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        if (best != null && bestDistance <= MATCH_THRESHOLD) {
            return ImageSearchResult.matched(best, bestDistance);
        }
        return ImageSearchResult.noMatch(best == null ? -1 : bestDistance);
    }
}
