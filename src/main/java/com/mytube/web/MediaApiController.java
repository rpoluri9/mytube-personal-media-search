package com.mytube.web;

import com.mytube.model.MediaItem;
import com.mytube.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST API for browsing and text-searching the library. */
@RestController
@RequestMapping("/api")
public class MediaApiController {

    private final MediaService mediaService;

    public MediaApiController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/media")
    public List<MediaItem> all() {
        return mediaService.findAll();
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<MediaItem> byId(@PathVariable Long id) {
        return mediaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Text search across title, description, tags and creator.
     * Title hits are ranked first.
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(name = "q", required = false) String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Query parameter 'q' is required."));
        }
        return ResponseEntity.ok(mediaService.search(q));
    }
}
