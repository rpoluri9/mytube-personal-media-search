package com.mytube;

import com.mytube.model.MediaItem;
import com.mytube.repo.MediaItemRepository;
import com.mytube.service.MediaService;
import com.mytube.service.image.CompositeImageMatchProvider;
import com.mytube.service.image.ImageMatchProvider;
import com.mytube.service.image.ImageSearchResult;
import com.mytube.service.image.LocalHashMatchProvider;
import com.mytube.service.web.PageRef;
import com.mytube.service.web.VisionWebDetectionProvider;
import com.mytube.service.web.WebEntity;
import com.mytube.service.web.WebIdentification;
import com.mytube.service.web.WebSearchException;
import com.mytube.service.web.YouTubeSearchService;
import com.mytube.service.web.YouTubeVideo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web image-search tests: Vision WEB_DETECTION parsing, YouTube search
 * parsing, and the composite provider. No real API keys or network — HTTP is
 * stubbed with realistic fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebImageSearchTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ImageMatchProvider imageMatchProvider; // the @Primary composite

    @Autowired
    LocalHashMatchProvider localProvider;

    @Autowired
    MediaItemRepository repository;

    @Autowired
    MediaService mediaService;

    // ---------- fixtures ----------
    private static final String VISION_FIXTURE = """
            {"responses":[{"webDetection":{
              "bestGuessLabels":[{"label":"RRR (film)","languageCode":"en"}],
              "webEntities":[
                {"entityId":"/m/0xyz123","score":4.2,"description":"RRR"},
                {"entityId":"/m/0abc456","score":1.8,"description":"S. S. Rajamouli"}],
              "fullMatchingImages":[{"url":"https://example.com/rrr-full.jpg"}],
              "partialMatchingImages":[],
              "pagesWithMatchingImages":[
                {"url":"https://en.wikipedia.org/wiki/RRR","pageTitle":"RRR - Wikipedia"}],
              "visuallySimilarImages":[
                {"url":"https://example.com/sim1.jpg"},
                {"url":"https://example.com/sim2.jpg"}]}}]}""";

    private static final String VISION_FIXTURE_NO_GUESS = """
            {"responses":[{"webDetection":{
              "bestGuessLabels":[],
              "webEntities":[],
              "pagesWithMatchingImages":[],
              "visuallySimilarImages":[
                {"url":"https://example.com/sim1.jpg"},
                {"url":"https://example.com/sim2.jpg"}]}}]}""";

    private static final String GOOGLE_ERROR = """
            {"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""";

    private static final String YT_FIXTURE = """
            {"kind":"youtube#searchListResponse",
             "pageInfo":{"totalResults":2,"resultsPerPage":2},
             "items":[
              {"kind":"youtube#searchResult",
               "id":{"kind":"youtube#video","videoId":"dQw4w9WgXcQ"},
               "snippet":{"publishedAt":"2022-03-24T10:00:00Z",
                 "channelId":"UC1234567890abcdef",
                 "title":"RRR - Official Trailer",
                 "description":"The official trailer for RRR...",
                 "thumbnails":{
                   "default":{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg"},
                   "medium":{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"}},
                 "channelTitle":"Movie Trailers"}},
              {"kind":"youtube#searchResult",
               "id":{"kind":"youtube#video","videoId":"abcdefghijk"},
               "snippet":{"publishedAt":"2022-01-05T10:00:00Z",
                 "channelId":"UC0987654321fedcba",
                 "title":"Naatu Naatu Full Video Song",
                 "description":"Watch the full video song...",
                 "thumbnails":{
                   "default":{"url":"https://i.ytimg.com/vi/abcdefghijk/default.jpg"}},
                 "channelTitle":"Music Label"}}]}""";

    // ---------- stub HTTP ----------
    record StubResponse(int status, String body) {}

    static ClientHttpRequestFactory stubFactory(Function<URI, StubResponse> router,
                                                AtomicBoolean called) {
        return stubFactory(router, called, null);
    }

    static ClientHttpRequestFactory stubFactory(Function<URI, StubResponse> router,
                                                AtomicBoolean called,
                                                java.util.concurrent.atomic.AtomicReference<String> capturedBody) {
        return (URI uri, HttpMethod method) -> new ClientHttpRequest() {
            private final ByteArrayOutputStream bodyBuf = new ByteArrayOutputStream();

            @Override
            public HttpMethod getMethod() { return method; }

            @Override
            public URI getURI() { return uri; }

            @Override
            public HttpHeaders getHeaders() { return new HttpHeaders(); }

            @Override
            public OutputStream getBody() { return bodyBuf; }

            @Override
            public ClientHttpResponse execute() {
                if (called != null) {
                    called.set(true);
                }
                if (capturedBody != null) {
                    capturedBody.set(bodyBuf.toString(StandardCharsets.UTF_8));
                }
                StubResponse r = router.apply(uri);
                return new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() {
                        return HttpStatusCode.valueOf(r.status());
                    }

                    @Override
                    @SuppressWarnings("deprecation")
                    public String getStatusText() { return ""; }

                    @Override
                    public void close() {}

                    @Override
                    public InputStream getBody() {
                        return new ByteArrayInputStream(
                                r.body().getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        HttpHeaders h = new HttpHeaders();
                        h.setContentType(MediaType.APPLICATION_JSON);
                        return h;
                    }
                };
            }
        };
    }

    private static RestClient stubVisionClient(String body, int status, AtomicBoolean called) {
        return RestClient.builder()
                .requestFactory(stubFactory(uri -> new StubResponse(status, body), called))
                .baseUrl("https://vision.googleapis.com")
                .build();
    }

    private static RestClient stubYouTubeClient(String body, int status, AtomicBoolean called) {
        return RestClient.builder()
                .requestFactory(stubFactory(uri -> new StubResponse(status, body), called))
                .baseUrl("https://www.googleapis.com")
                .build();
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] thumbnailBytes(String title) throws Exception {
        MediaItem item = mediaService.findByTitle(title).orElseThrow();
        return Files.readAllBytes(Path.of("./data/thumbnails", item.getThumbnailFilename()));
    }

    // ---------- Vision provider ----------
    @Test
    void vision_parsesFixtureIntoIdentification() throws Exception {
        VisionWebDetectionProvider vision =
                new VisionWebDetectionProvider("test-key",
                        stubVisionClient(VISION_FIXTURE, 200, null));

        WebIdentification ident = vision.identify(tinyPng());

        assertEquals("RRR (film)", ident.getBestGuessLabel());
        assertEquals(2, ident.getEntities().size());
        assertEquals("RRR", ident.getEntities().get(0).description());
        assertEquals("S. S. Rajamouli", ident.getEntities().get(1).description());
        assertEquals(2, ident.getVisuallySimilarImageUrls().size());
        assertEquals("https://example.com/sim1.jpg", ident.getVisuallySimilarImageUrls().get(0));
        assertEquals(1, ident.getPagesWithMatchingImages().size());
        assertEquals("https://en.wikipedia.org/wiki/RRR",
                ident.getPagesWithMatchingImages().get(0).url());
        assertFalse(ident.isEmpty());
    }

    @Test
    void vision_unsureResult_stillShowsSimilarImages() throws Exception {
        VisionWebDetectionProvider vision =
                new VisionWebDetectionProvider("test-key",
                        stubVisionClient(VISION_FIXTURE_NO_GUESS, 200, null));

        WebIdentification ident = vision.identify(tinyPng());

        assertNull(ident.getBestGuessLabel());
        assertTrue(ident.getEntities().isEmpty());
        assertFalse(ident.isEmpty(), "similar images alone must not be treated as empty");
        assertEquals(2, ident.getVisuallySimilarImageUrls().size());
    }

    @Test
    void vision_blankKey_isInert_noHttpCall() {
        AtomicBoolean called = new AtomicBoolean(false);
        VisionWebDetectionProvider vision =
                new VisionWebDetectionProvider("",
                        stubVisionClient(VISION_FIXTURE, 200, called));

        ImageSearchResult result = vision.findBestMatch(new byte[]{1, 2, 3});

        assertFalse(result.isMatched());
        assertFalse(result.hasWebData());
        assertNull(result.getWebError());
        assertFalse(called.get(), "no HTTP call may happen without a key");
    }

    @Test
    void vision_apiError_becomesWebError_notThrow() throws Exception {
        VisionWebDetectionProvider vision =
                new VisionWebDetectionProvider("dummy-key",
                        stubVisionClient(GOOGLE_ERROR, 400, null));

        ImageSearchResult result = vision.findBestMatch(tinyPng());

        assertFalse(result.isMatched());
        assertNotNull(result.getWebError());
        assertTrue(result.getWebError().contains("API key"),
                "error must clearly mention the API key, was: " + result.getWebError());
    }

    // ---------- YouTube service ----------
    @Test
    void youtube_parsesFixtureIntoVideos() {
        YouTubeSearchService yt =
                new YouTubeSearchService("test-key", 5,
                        stubYouTubeClient(YT_FIXTURE, 200, null));

        List<YouTubeVideo> videos = yt.searchVideos("rrr");

        assertEquals(2, videos.size());
        YouTubeVideo v0 = videos.get(0);
        assertEquals("dQw4w9WgXcQ", v0.videoId());
        assertEquals("RRR - Official Trailer", v0.title());
        assertEquals("Movie Trailers", v0.channelTitle());
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", v0.videoUrl());
        assertEquals("https://www.youtube.com/channel/UC1234567890abcdef", v0.channelUrl());
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", v0.thumbnailUrl());
        // second video has only the default thumbnail -> falls back to it
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/default.jpg",
                videos.get(1).thumbnailUrl());
    }

    @Test
    void youtube_apiError_throwsWithMessage() {
        YouTubeSearchService yt =
                new YouTubeSearchService("dummy-key", 5,
                        stubYouTubeClient(GOOGLE_ERROR, 403, null));

        WebSearchException e = assertThrows(WebSearchException.class,
                () -> yt.searchVideos("rrr"));
        assertTrue(e.getMessage().contains("YouTube"), "was: " + e.getMessage());
    }

    @Test
    void youtube_blankKey_returnsEmpty_noHttpCall() {
        AtomicBoolean called = new AtomicBoolean(false);
        YouTubeSearchService yt =
                new YouTubeSearchService("", 5,
                        stubYouTubeClient(YT_FIXTURE, 200, called));

        assertTrue(yt.searchVideos("rrr").isEmpty());
        assertFalse(called.get(), "no HTTP call may happen without a key");
    }

    // ---------- composite ----------
    @Test
    void composite_noKey_behavesLikeV1() throws Exception {
        // application.properties ships with a blank key.
        ImageSearchResult result = imageMatchProvider.findBestMatch(thumbnailBytes("RRR"));

        assertTrue(result.isMatched());
        assertEquals("RRR", result.getMatch().getTitle());
        assertNull(result.getWebIdentification());
        assertTrue(result.getYouTubeVideos().isEmpty());
        assertNull(result.getWebError());
        assertFalse(result.hasWebData());
    }

    @Test
    void composite_withKey_mergesLocalAndWeb() throws Exception {
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("test-key",
                stubVisionClient(VISION_FIXTURE, 200, null));
        YouTubeSearchService yt = new YouTubeSearchService("test-key", 5,
                stubYouTubeClient(YT_FIXTURE, 200, null));
        CompositeImageMatchProvider composite =
                new CompositeImageMatchProvider(localProvider, vision, yt);

        ImageSearchResult result = composite.findBestMatch(thumbnailBytes("RRR"));

        assertTrue(result.isMatched(), "local library match must still work");
        assertEquals("RRR", result.getMatch().getTitle());
        assertNotNull(result.getWebIdentification());
        assertEquals("RRR (film)", result.getWebIdentification().getBestGuessLabel());
        assertEquals(2, result.getYouTubeVideos().size());
        assertEquals("Movie Trailers", result.getYouTubeVideos().get(0).channelTitle());
        assertNull(result.getWebError());
        assertTrue(result.hasWebData());
    }

    @Test
    void composite_visionFails_localMatchStillReturned() throws Exception {
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("dummy-key",
                stubVisionClient(GOOGLE_ERROR, 400, null));
        YouTubeSearchService yt = new YouTubeSearchService("dummy-key", 5,
                stubYouTubeClient(YT_FIXTURE, 200, null));
        CompositeImageMatchProvider composite =
                new CompositeImageMatchProvider(localProvider, vision, yt);

        ImageSearchResult result = composite.findBestMatch(thumbnailBytes("RRR"));

        assertTrue(result.isMatched(), "local match must survive a web failure");
        assertEquals("RRR", result.getMatch().getTitle());
        assertNotNull(result.getWebError());
        assertTrue(result.getYouTubeVideos().isEmpty());
    }

    @Test
    void composite_queryFallback_prefersEntitiesThenPages() {
        assertEquals("Dune",
                CompositeImageMatchProvider.youTubeQueryFor(new WebIdentification(null,
                        List.of(new WebEntity("Dune", 2.0)), List.of(), List.of(), null)));
        assertEquals("RRR - Wikipedia",
                CompositeImageMatchProvider.youTubeQueryFor(new WebIdentification(null,
                        List.of(), List.of(),
                        List.of(new PageRef("https://example.com", "RRR - Wikipedia")), null)));
        assertNull(CompositeImageMatchProvider.youTubeQueryFor(WebIdentification.EMPTY));
    }

    @Test
    void composite_query_enrichesShortBaseWithScreenText() {
        // "Funmoji" (entity) + on-screen title -> "Funmoji College Series EP-10 ..."
        String q = CompositeImageMatchProvider.youTubeQueryFor(new WebIdentification(null,
                List.of(new WebEntity("Funmoji", 2.2)), List.of(), List.of(),
                "గోరాబిలి College Series EP-10 MCA"));
        assertTrue(q.startsWith("Funmoji"), "was: " + q);
        assertTrue(q.contains("EP-10"), "was: " + q);
        assertTrue(q.length() <= 90, "was: " + q);
    }

    // ---------- API shape ----------
    @Test
    void api_includesWebFields_noKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rrr.png", "image/png",
                thumbnailBytes("RRR"));

        mockMvc.perform(multipart("/api/search/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.title").value("RRR"))
                .andExpect(jsonPath("$.youTubeVideos").isArray())
                .andExpect(jsonPath("$.youTubeVideos").isEmpty())
                .andExpect(jsonPath("$.webError").doesNotExist());
    }

    // ---------- acceptance: the two real screenshots ----------
    // Fixture A mirrors the Bigg Boss Season 10 Telugu Shorts screenshot:
    // host Nagarjuna, Telugu "ఈరోజు 9.30PM" banner, @TheEditpioneer.
    private static final String VISION_BIGGBOSS = """
            {"responses":[{
              "webDetection":{
                "bestGuessLabels":[{"label":"Bigg Boss Telugu Season 10","languageCode":"en"}],
                "webEntities":[
                  {"entityId":"/m/bb1","score":5.1,"description":"Bigg Boss"},
                  {"entityId":"/m/nag1","score":3.8,"description":"Nagarjuna (actor)"}],
                "pagesWithMatchingImages":[
                  {"url":"https://www.youtube.com/shorts/B1gGB055Sh0",
                   "pageTitle":"BIGBOSS Season 10 Telugu Day 37 | Naresh - YouTube"},
                  {"url":"https://en.wikipedia.org/wiki/Bigg_Boss_Telugu_season_10",
                   "pageTitle":"Bigg Boss (Telugu season 10) - Wikipedia"}],
                "visuallySimilarImages":[{"url":"https://example.com/bb-sim.jpg"}]},
              "textAnnotations":[
                {"description":"full","boundingPoly":{"vertices":[]}},
                {"description":"BIGG","boundingPoly":{"vertices":[{"x":300,"y":1290},{"x":560,"y":1290},{"x":560,"y":1420},{"x":300,"y":1420}]}},
                {"description":"BOSS","boundingPoly":{"vertices":[{"x":580,"y":1290},{"x":840,"y":1290},{"x":840,"y":1420},{"x":580,"y":1420}]}},
                {"description":"ఈరోజు","boundingPoly":{"vertices":[{"x":330,"y":950},{"x":470,"y":950},{"x":470,"y":1000},{"x":330,"y":1000}]}},
                {"description":"9.30PM","boundingPoly":{"vertices":[{"x":620,"y":950},{"x":760,"y":950},{"x":760,"y":1000},{"x":620,"y":1000}]}},
                {"description":"Subscribe","boundingPoly":{"vertices":[{"x":400,"y":1560},{"x":560,"y":1560},{"x":560,"y":1600},{"x":400,"y":1600}]}}]
            }]}""";

    // Fixture B mirrors the @Funmoji "College Series EP-10" screenshot with a
    // Bluehost ad banner at the bottom: Vision is unsure (no best guess), the
    // ad text must NOT win the on-screen-text ranking.
    private static final String VISION_FUNMOJI = """
            {"responses":[{
              "webDetection":{
                "bestGuessLabels":[],
                "webEntities":[
                  {"entityId":"/m/fm1","score":2.2,"description":"Funmoji"}],
                "pagesWithMatchingImages":[],
                "visuallySimilarImages":[]},
              "textAnnotations":[
                {"description":"full text","boundingPoly":{"vertices":[]}},
                {"description":"గోరాబిలి","boundingPoly":{"vertices":[{"x":20,"y":520},{"x":200,"y":520},{"x":200,"y":580},{"x":20,"y":580}]}},
                {"description":"College","boundingPoly":{"vertices":[{"x":220,"y":520},{"x":400,"y":520},{"x":400,"y":580},{"x":220,"y":580}]}},
                {"description":"Series","boundingPoly":{"vertices":[{"x":420,"y":520},{"x":560,"y":520},{"x":560,"y":580},{"x":420,"y":580}]}},
                {"description":"EP-10","boundingPoly":{"vertices":[{"x":580,"y":520},{"x":700,"y":520},{"x":700,"y":580},{"x":580,"y":580}]}},
                {"description":"MCA","boundingPoly":{"vertices":[{"x":720,"y":520},{"x":800,"y":520},{"x":800,"y":580},{"x":720,"y":580}]}},
                {"description":"One","boundingPoly":{"vertices":[{"x":60,"y":1350},{"x":180,"y":1350},{"x":180,"y":1450},{"x":60,"y":1450}]}},
                {"description":"Bluehost","boundingPoly":{"vertices":[{"x":80,"y":1200},{"x":320,"y":1200},{"x":320,"y":1250},{"x":80,"y":1250}]}},
                {"description":"6.6K","boundingPoly":{"vertices":[{"x":130,"y":640},{"x":210,"y":640},{"x":210,"y":665},{"x":130,"y":665}]}}
              ]
            }]}
            """;

    private static final String YT_VIDEOS_LIST = """
            {"kind":"youtube#videoListResponse","pageInfo":{"totalResults":1,"resultsPerPage":1},
             "items":[{"kind":"youtube#video","id":"B1gGB055Sh0",
               "snippet":{"title":"BIGBOSS Season 10 Telugu Day 37 | Naresh",
                 "channelId":"UCTheEditpioneer1","channelTitle":"TheEditpioneer",
                 "description":"Day 37 Naresh special promo...",
                 "thumbnails":{"default":{"url":"https://i.ytimg.com/vi/B1gGB055Sh0/default.jpg"}}}}]}""";

    @Test
    void vision_requestAsksForWebAndTextDetection() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> body =
                new java.util.concurrent.atomic.AtomicReference<>();
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("test-key",
                RestClient.builder()
                        .requestFactory(stubFactory(
                                uri -> new StubResponse(200, VISION_FIXTURE), null, body))
                        .baseUrl("https://vision.googleapis.com").build());

        vision.identify(tinyPng());

        assertNotNull(body.get());
        assertTrue(body.get().contains("WEB_DETECTION"), body.get());
        assertTrue(body.get().contains("TEXT_DETECTION"), body.get());
    }

    @Test
    void vision_funmojiShot_adBannerDoesNotWinOnScreenText() throws Exception {
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("test-key",
                stubVisionClient(VISION_FUNMOJI, 200, null));

        WebIdentification ident = vision.identify(tinyPng());

        assertNull(ident.getBestGuessLabel(), "Vision is unsure here");
        assertEquals("Funmoji", ident.getEntities().get(0).description());
        assertNotNull(ident.getOnScreenText());
        assertTrue(ident.getOnScreenText().contains("College"), ident.getOnScreenText());
        assertTrue(ident.getOnScreenText().contains("EP-10"), ident.getOnScreenText());
        assertFalse(ident.getOnScreenText().contains("Bluehost"), ident.getOnScreenText());
        assertFalse(ident.getOnScreenText().contains("One"), ident.getOnScreenText());
    }

    @Test
    void youtube_extractsVideoIds_fromWatchShortsAndShortUrls() {
        assertEquals(
                List.of("dQw4w9WgXcQ", "B1gGB055Sh0", "abcdefghijk"),
                YouTubeSearchService.extractYouTubeVideoIds(List.of(
                        new PageRef("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s", "t"),
                        new PageRef("https://www.youtube.com/shorts/B1gGB055Sh0", "s"),
                        new PageRef("https://youtu.be/abcdefghijk", "y"),
                        new PageRef("https://en.wikipedia.org/wiki/X", "w"),
                        new PageRef("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dup"))));
    }

    @Test
    void youtube_getVideoDetails_parsesPlainStringIds() {
        YouTubeSearchService yt = new YouTubeSearchService("test-key", 5,
                stubYouTubeClient(YT_VIDEOS_LIST, 200, null));

        List<YouTubeVideo> videos = yt.getVideoDetails(List.of("B1gGB055Sh0"));

        assertEquals(1, videos.size());
        assertEquals("BIGBOSS Season 10 Telugu Day 37 | Naresh", videos.get(0).title());
        assertEquals("TheEditpioneer", videos.get(0).channelTitle());
        assertEquals("https://www.youtube.com/channel/UCTheEditpioneer1",
                videos.get(0).channelUrl());
        assertEquals("https://www.youtube.com/watch?v=B1gGB055Sh0", videos.get(0).videoUrl());
    }

    @Test
    void composite_biggBossShort_exactChannelFirstThenRelated() throws Exception {
        java.util.concurrent.atomic.AtomicReference<URI> searchUri =
                new java.util.concurrent.atomic.AtomicReference<>();
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("test-key",
                stubVisionClient(VISION_BIGGBOSS, 200, null));
        YouTubeSearchService yt = new YouTubeSearchService("test-key", 5,
                RestClient.builder()
                        .requestFactory(stubFactory(uri -> {
                            if (uri.getPath().contains("/videos")) {
                                return new StubResponse(200, YT_VIDEOS_LIST);
                            }
                            searchUri.set(uri);
                            return new StubResponse(200, YT_FIXTURE);
                        }, null))
                        .baseUrl("https://www.googleapis.com").build());
        CompositeImageMatchProvider composite =
                new CompositeImageMatchProvider(localProvider, vision, yt);

        ImageSearchResult result = composite.findBestMatch(thumbnailBytes("RRR"));

        // 1) exact original video first, with the true channel name + link
        assertFalse(result.getYouTubeVideos().isEmpty());
        YouTubeVideo first = result.getYouTubeVideos().get(0);
        assertEquals("B1gGB055Sh0", first.videoId());
        assertEquals("TheEditpioneer", first.channelTitle());
        assertTrue(first.channelUrl().contains("UCTheEditpioneer1"));
        // 2) related videos follow, no duplicates
        assertTrue(result.getYouTubeVideos().size() >= 3, "exact + related");
        long distinct = result.getYouTubeVideos().stream()
                .map(YouTubeVideo::videoId).distinct().count();
        assertEquals(result.getYouTubeVideos().size(), distinct, "no duplicate videos");
        // 3) the related-search query targets the content, not UI chrome
        assertNotNull(searchUri.get());
        assertTrue(searchUri.get().getQuery().contains("Bigg"),
                searchUri.get().getQuery());
    }

    @Test
    void composite_funmojiShot_queryTargetsContentNotAd() throws Exception {
        java.util.concurrent.atomic.AtomicReference<URI> searchUri =
                new java.util.concurrent.atomic.AtomicReference<>();
        VisionWebDetectionProvider vision = new VisionWebDetectionProvider("test-key",
                stubVisionClient(VISION_FUNMOJI, 200, null));
        YouTubeSearchService yt = new YouTubeSearchService("test-key", 5,
                RestClient.builder()
                        .requestFactory(stubFactory(uri -> {
                            searchUri.set(uri);
                            return new StubResponse(200, YT_FIXTURE);
                        }, null))
                        .baseUrl("https://www.googleapis.com").build());
        CompositeImageMatchProvider composite =
                new CompositeImageMatchProvider(localProvider, vision, yt);

        ImageSearchResult result = composite.findBestMatch(thumbnailBytes("RRR"));

        assertNotNull(searchUri.get(), "a search must run for the unsure image");
        String q = searchUri.get().getQuery();
        assertTrue(q.contains("Funmoji"), q);
        assertTrue(q.contains("EP-10"), q);
        assertFalse(q.contains("Bluehost"), q);
        assertFalse(result.getYouTubeVideos().isEmpty());
    }
}
