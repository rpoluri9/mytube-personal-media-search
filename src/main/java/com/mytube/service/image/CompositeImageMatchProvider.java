package com.mytube.service.image;

import com.mytube.service.web.PageRef;
import com.mytube.service.web.VisionWebDetectionProvider;
import com.mytube.service.web.WebEntity;
import com.mytube.service.web.WebIdentification;
import com.mytube.service.web.WebSearchException;
import com.mytube.service.web.YouTubeSearchService;
import com.mytube.service.web.YouTubeVideo;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composite "search by image": always runs the local perceptual-hash match
 * against the personal library (v1 behaviour, works with no API key), and —
 * when {@code mytube.google.api-key} is configured — also asks Google what
 * the picture is (Vision WEB_DETECTION + TEXT_DETECTION: works for any
 * screenshot or camera photo of a screen) and resolves related YouTube
 * videos. Web failures never break the local result: they are reported as
 * {@code webError} / {@code youTubeError} on the merged result.
 *
 * <p>YouTube results are ordered: the exact original video first (when
 * Vision's matching pages link to a YouTube watch/Shorts URL, resolved via
 * {@code videos.list}), then related videos from a text search on the
 * identified topic.
 */
@Service
@Primary
public class CompositeImageMatchProvider implements ImageMatchProvider {

    private final LocalHashMatchProvider localProvider;
    private final VisionWebDetectionProvider visionProvider;
    private final YouTubeSearchService youTubeSearchService;

    public CompositeImageMatchProvider(LocalHashMatchProvider localProvider,
                                       VisionWebDetectionProvider visionProvider,
                                       YouTubeSearchService youTubeSearchService) {
        this.localProvider = localProvider;
        this.visionProvider = visionProvider;
        this.youTubeSearchService = youTubeSearchService;
    }

    @Override
    public ImageSearchResult findBestMatch(byte[] imageBytes) {
        // Local match first: validates the image and is the no-key behaviour.
        ImageSearchResult local = localProvider.findBestMatch(imageBytes);

        if (!visionProvider.isEnabled()) {
            return local;
        }

        WebIdentification identification;
        String webError = null;
        try {
            identification = visionProvider.identify(imageBytes);
        } catch (WebSearchException e) {
            identification = WebIdentification.EMPTY;
            webError = e.getMessage();
        }

        List<YouTubeVideo> videos = new ArrayList<>();
        String youTubeError = null;

        // 1) Exact original video(s): Vision's matching pages often link the
        //    YouTube watch/Shorts page the screenshot came from — resolve it
        //    for the true channel name + channel link (1 quota unit).
        for (String id : YouTubeSearchService.extractYouTubeVideoIds(
                identification.getPagesWithMatchingImages())) {
            try {
                videos.addAll(youTubeSearchService.getVideoDetails(List.of(id)));
            } catch (WebSearchException e) {
                youTubeError = e.getMessage();
                break;
            }
        }

        // 2) Related videos: text search on the identified topic.
        String query = youTubeQueryFor(identification);
        if (query != null) {
            try {
                Set<String> seen = videos.stream()
                        .map(YouTubeVideo::videoId)
                        .collect(Collectors.toSet());
                for (YouTubeVideo v : youTubeSearchService.searchVideos(query)) {
                    if (seen.add(v.videoId())) {
                        videos.add(v);
                    }
                }
            } catch (WebSearchException e) {
                youTubeError = youTubeError == null
                        ? e.getMessage() : youTubeError + " " + e.getMessage();
            }
        }

        return ImageSearchResult.withWeb(local, identification, videos, webError, youTubeError);
    }

    /**
     * Builds the YouTube search query from the identification.
     * Priority: best-guess label, else top web entity, else prominent
     * on-screen text, else top matching page title. When the guess/entity is
     * short, on-screen text with novel tokens is appended (e.g.
     * "Funmoji" + "College Series EP-10") — ad-banner text never reaches here
     * because it scores low in {@code extractProminentText}.
     */
    public static String youTubeQueryFor(WebIdentification identification) {
        if (identification == null || identification.isEmpty()) {
            return null;
        }
        String base = null;
        if (notBlank(identification.getBestGuessLabel())) {
            base = identification.getBestGuessLabel().trim();
        } else if (!identification.getEntities().isEmpty()) {
            base = identification.getEntities().get(0).description();
        } else if (notBlank(identification.getOnScreenText())) {
            return identification.getOnScreenText();
        } else {
            List<PageRef> pages = identification.getPagesWithMatchingImages();
            if (!pages.isEmpty()) {
                String title = pages.get(0).pageTitle();
                return notBlank(title) ? title.trim() : pages.get(0).url();
            }
            return null;
        }

        String screen = identification.getOnScreenText();
        if (notBlank(screen)) {
            Set<String> baseTokens = new HashSet<>(Arrays.asList(
                    base.toLowerCase().split("[^a-z0-9]+")));
            String novel = Arrays.stream(screen.split("\\s+"))
                    .filter(t -> t.length() > 2 && !baseTokens.contains(t.toLowerCase()))
                    .collect(Collectors.joining(" "));
            String combined = (base + " " + novel).trim();
            if (!novel.isBlank() && combined.length() <= 90) {
                base = combined;
            }
        }
        return base;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
