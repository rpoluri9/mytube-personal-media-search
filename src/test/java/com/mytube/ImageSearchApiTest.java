package com.mytube;

import com.mytube.model.MediaItem;
import com.mytube.repo.MediaItemRepository;
import com.mytube.service.MediaService;
import com.mytube.service.ThumbnailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack API tests: text search, media endpoints, and the multipart
 * image-search endpoint (real generated thumbnails must match).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageSearchApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MediaService mediaService;

    @Autowired
    MediaItemRepository repository;

    @Autowired
    ThumbnailService thumbnailService;

    // ---------- text search / media ----------
    @Test
    void getAllMedia_returnsSixteen() throws Exception {
        mockMvc.perform(get("/api/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));
    }

    @Test
    void search_naatu_findsSong() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "naatu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Naatu Naatu"));
    }

    @Test
    void search_titleHitsRankedFirst() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "rrr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("RRR"));
    }

    @Test
    void search_blankQuery_badRequest() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMediaById_includesRelatedItems() throws Exception {
        MediaItem rrr = repository.findByTitle("RRR").orElseThrow();
        mockMvc.perform(get("/api/media/{id}", rrr.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("RRR"))
                .andExpect(jsonPath("$.relatedItems.length()").value(3));
    }

    @Test
    void getMediaById_unknownId_404() throws Exception {
        mockMvc.perform(get("/api/media/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------- image search ----------
    @Test
    void imageUpload_generatedThumbnail_matchesCorrectItem() throws Exception {
        // Upload the exact thumbnail bytes the seeder generated for "Naatu Naatu".
        MediaItem song = mediaService.findByTitle("Naatu Naatu").orElseThrow();
        byte[] png = Files.readAllBytes(
                Path.of("./data/thumbnails", song.getThumbnailFilename()));
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", png);

        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").exists())
                .andExpect(jsonPath("$.match.title").value("Naatu Naatu"))
                .andExpect(jsonPath("$.match.type").value("SONG"))
                .andExpect(jsonPath("$.hammingDistance").value(0));
    }

    @Test
    void imageUpload_resizedThumbnail_stillMatches() throws Exception {
        // A half-size "screenshot" of the RRR thumbnail must still match RRR.
        MediaItem rrr = mediaService.findByTitle("RRR").orElseThrow();
        byte[] png = Files.readAllBytes(
                Path.of("./data/thumbnails", rrr.getThumbnailFilename()));
        BufferedImage full = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        BufferedImage half = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = half.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(full, 0, 0, 160, 90, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(half, "png", out);
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", out.toByteArray());

        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.title").value("RRR"));
    }

    @Test
    void imageUpload_garbageFile_badRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain",
                "this is not an image".getBytes());
        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("could not be read as an image")));
    }

    @Test
    void imageUpload_emptyFile_badRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageUpload_unrelatedNoise_noMatch() throws Exception {
        // Random noise is far from every thumbnail -> 200 with match:null.
        Random rnd = new Random(42);
        BufferedImage noise = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                noise.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noise, "png", out);
        MockMultipartFile file = new MockMultipartFile("file", "noise.png", "image/png", out.toByteArray());

        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match", nullValue()));
    }

    // ---------- web pages ----------
    @Test
    void homePage_returns200Html() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("MyTube")));
    }

    @Test
    void detailPage_returns200Html() throws Exception {
        MediaItem rrr = repository.findByTitle("RRR").orElseThrow();
        mockMvc.perform(get("/media/{id}", rrr.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Naatu Naatu")));
    }
}
