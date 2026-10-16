package com.mytube.repo;

import com.mytube.model.MediaItem;
import com.mytube.model.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByTitle(String title);

    List<MediaItem> findByType(MediaType type);

    List<MediaItem> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
            String title, String description, String tags, String creator);
}
