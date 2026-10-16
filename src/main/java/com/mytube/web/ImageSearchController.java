package com.mytube.web;

import com.mytube.service.image.ImageMatchProvider;
import com.mytube.service.image.ImageSearchResult;
import com.mytube.service.image.InvalidImageException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * "Search by image": upload a screenshot / picture (from the phone gallery or
 * disk) and MyTube finds the matching item in your personal library using the
 * perceptual dHash, then shows everything it knows about it — songs, story /
 * plot, cast and related media.
 */
@Controller
public class ImageSearchController {

    private final ImageMatchProvider imageMatchProvider;

    public ImageSearchController(ImageMatchProvider imageMatchProvider) {
        this.imageMatchProvider = imageMatchProvider;
    }

    // ---------- JSON API ----------
    @PostMapping(value = "/api/search/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<ImageSearchResponse> searchByImageApi(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ImageSearchResponse.error(
                            "No file was uploaded. Please choose an image file (PNG or JPEG)."));
        }
        try {
            ImageSearchResult result = imageMatchProvider.findBestMatch(file.getBytes());
            if (result.isMatched()) {
                return ResponseEntity.ok(
                        ImageSearchResponse.matched(result.getMatch(), result.getHammingDistance()));
            }
            return ResponseEntity.ok(ImageSearchResponse.noMatch(
                    result.getHammingDistance() < 0 ? null : result.getHammingDistance()));
        } catch (InvalidImageException e) {
            return ResponseEntity.badRequest().body(ImageSearchResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(ImageSearchResponse.error("Could not read the uploaded file."));
        }
    }

    // ---------- Web form (Thymeleaf) ----------
    @PostMapping(value = "/search/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String searchByImageWeb(@RequestParam("file") MultipartFile file, Model model) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Please choose an image file to upload first.");
            return "image-result";
        }
        try {
            ImageSearchResult result = imageMatchProvider.findBestMatch(file.getBytes());
            model.addAttribute("result", result);
            if (result.isMatched()) {
                model.addAttribute("item", result.getMatch());
                model.addAttribute("songs", result.getMatch().relatedOfType(com.mytube.model.MediaType.SONG));
                model.addAttribute("related", result.getMatch().getRelatedItems());
            }
        } catch (InvalidImageException e) {
            model.addAttribute("error", e.getMessage());
        } catch (IOException e) {
            model.addAttribute("error", "Could not read the uploaded file.");
        }
        return "image-result";
    }
}
