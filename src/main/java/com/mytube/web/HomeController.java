package com.mytube.web;

import com.mytube.model.MediaType;
import com.mytube.service.MediaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Thymeleaf pages: home grid, detail page. */
@Controller
public class HomeController {

    private final MediaService mediaService;

    public HomeController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "q", required = false) String q, Model model) {
        if (q != null && !q.isBlank()) {
            model.addAttribute("items", mediaService.search(q));
            model.addAttribute("query", q);
        } else {
            model.addAttribute("items", mediaService.findAll());
        }
        return "index";
    }

    @GetMapping("/media/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return mediaService.findById(id)
                .map(item -> {
                    model.addAttribute("item", item);
                    model.addAttribute("songs", item.relatedOfType(MediaType.SONG));
                    model.addAttribute("related", item.getRelatedItems());
                    return "detail";
                })
                .orElse("redirect:/");
    }
}
