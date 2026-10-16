package com.mytube.service;

import com.mytube.model.MediaItem;
import com.mytube.repo.MediaItemRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Text search over the library: case-insensitive match on title, description,
 * tags and creator, with title hits ranked first.
 */
@Service
public class MediaService {

    private final MediaItemRepository repository;

    public MediaService(MediaItemRepository repository) {
        this.repository = repository;
    }

    public List<MediaItem> findAll() {
        return repository.findAll();
    }

    public Optional<MediaItem> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<MediaItem> findByTitle(String title) {
        return repository.findByTitle(title);
    }

    public List<MediaItem> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        String lower = q.toLowerCase();
        List<MediaItem> hits = repository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(q, q, q, q);
        // Title hits first, then everything else, each group alphabetical.
        hits.sort(Comparator
                .comparing((MediaItem m) -> m.getTitle().toLowerCase().contains(lower) ? 0 : 1)
                .thenComparing(MediaItem::getTitle));
        return hits;
    }
}
