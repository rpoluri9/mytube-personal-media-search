package com.mytube;

import com.mytube.model.MediaItem;
import com.mytube.model.MediaType;
import com.mytube.repo.MediaItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the startup seed: 16 items, thumbnails + hashes present,
 * relationships wired.
 */
@SpringBootTest
@Transactional
class SeedDataTest {

    @Autowired
    MediaItemRepository repository;

    @Test
    void seedCount_isSixteen() {
        assertEquals(16, repository.count());
    }

    @Test
    void everyItem_hasThumbnailFileAndHash() {
        for (MediaItem item : repository.findAll()) {
            assertNotNull(item.getThumbnailFilename(), item.getTitle());
            assertNotNull(item.getImageHash(), item.getTitle());
            assertTrue(Files.exists(Path.of("./data/thumbnails", item.getThumbnailFilename())),
                    "thumbnail missing for " + item.getTitle());
        }
    }

    @Test
    void rrr_linksToTwoSongsAndAStory() {
        MediaItem rrr = repository.findByTitle("RRR").orElseThrow();
        assertEquals(2, rrr.relatedOfType(MediaType.SONG).size());
        assertTrue(rrr.relatedOfType(MediaType.SONG).stream()
                .anyMatch(s -> s.getTitle().equals("Naatu Naatu")));
        assertTrue(rrr.relatedOfType(MediaType.SONG).stream()
                .anyMatch(s -> s.getTitle().equals("Etthara Jenda")));
        assertEquals(1, rrr.relatedOfType(MediaType.STORY).size());
    }
}
