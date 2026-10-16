package com.mytube;

import com.mytube.model.MediaItem;
import com.mytube.model.MediaType;
import com.mytube.repo.MediaItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository slice tests (in-memory H2, no seeder).
 */
@DataJpaTest
class MediaItemRepositoryTest {

    @Autowired
    MediaItemRepository repository;

    @Test
    void saveAndFindByTitle() {
        MediaItem m = new MediaItem("Test Movie", MediaType.MOVIE);
        m.setCreator("Tester");
        repository.save(m);

        assertTrue(repository.findByTitle("Test Movie").isPresent());
        assertEquals("Tester", repository.findByTitle("Test Movie").get().getCreator());
    }

    @Test
    void derivedQuery_matchesAcrossFields() {
        MediaItem m = new MediaItem("Mystery Film", MediaType.MOVIE);
        m.setCreator("Jane Doe");
        m.setTags("xyzzy-tag");
        m.setDescription("a strange tale");
        repository.save(m);

        List<MediaItem> byTag = repository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                        "xyzzy-tag", "xyzzy-tag", "xyzzy-tag", "xyzzy-tag");
        assertEquals(1, byTag.size());

        List<MediaItem> byCreator = repository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                        "jane", "jane", "jane", "jane");
        assertEquals(1, byCreator.size());
    }

    @Test
    void relatedItems_persistedThroughJoinTable() {
        MediaItem movie = new MediaItem("Parent Movie", MediaType.MOVIE);
        MediaItem song = new MediaItem("Child Song", MediaType.SONG);
        movie.addRelated(song);
        repository.save(movie);
        repository.save(song);
        repository.flush();

        MediaItem reloaded = repository.findByTitle("Parent Movie").orElseThrow();
        assertEquals(1, reloaded.getRelatedItems().size());
        assertEquals("Child Song", reloaded.getRelatedItems().iterator().next().getTitle());
        // bidirectional
        assertTrue(repository.findByTitle("Child Song").orElseThrow()
                .getRelatedItems().stream().anyMatch(i -> i.getTitle().equals("Parent Movie")));
    }
}
