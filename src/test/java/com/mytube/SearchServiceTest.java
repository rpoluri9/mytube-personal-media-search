package com.mytube;

import com.mytube.model.MediaItem;
import com.mytube.model.MediaType;
import com.mytube.repo.MediaItemRepository;
import com.mytube.service.MediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for text search ranking, with a mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    MediaItemRepository repository;

    @InjectMocks
    MediaService mediaService;

    private MediaItem item(String title, String creator, String tags) {
        MediaItem m = new MediaItem(title, MediaType.MOVIE);
        m.setCreator(creator);
        m.setTags(tags);
        m.setDescription("desc of " + title);
        return m;
    }

    @Test
    void titleHits_rankedFirst() {
        MediaItem tagHit = item("Some Random Film", "Anon", "rrr,action");
        MediaItem titleHit = item("RRR", "S. S. Rajamouli", "action");
        when(repository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(List.of(tagHit, titleHit)));

        List<MediaItem> results = mediaService.search("rrr");
        assertEquals(2, results.size());
        assertEquals("RRR", results.get(0).getTitle());
    }

    @Test
    void search_isCaseInsensitive() {
        MediaItem m = item("Naatu Naatu", "M. M. Keeravani", "song");
        when(repository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(List.of(m)));

        assertEquals(1, mediaService.search("NAATU").size());
        assertEquals(1, mediaService.search("naatu").size());
    }

    @Test
    void matches_creatorField() {
        MediaItem m = item("A Time of Quiet Between the Storms", "Hans Zimmer", "score");
        when(repository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(List.of(m)));

        List<MediaItem> results = mediaService.search("zimmer");
        assertEquals(1, results.size());
        assertEquals("Hans Zimmer", results.get(0).getCreator());
    }

    @Test
    void blankQuery_returnsEmpty() {
        assertTrue(mediaService.search("").isEmpty());
        assertTrue(mediaService.search("   ").isEmpty());
        assertTrue(mediaService.search(null).isEmpty());
    }

    @Test
    void nonTitleHits_sortedAlphabetically() {
        MediaItem b = item("Zebra Song", "Anon", "rrr");
        MediaItem a = item("Apple Song", "Anon", "rrr");
        when(repository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrTagsContainingIgnoreCaseOrCreatorContainingIgnoreCase(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(List.of(b, a)));

        List<MediaItem> results = mediaService.search("rrr");
        assertEquals("Apple Song", results.get(0).getTitle());
        assertEquals("Zebra Song", results.get(1).getTitle());
    }
}
