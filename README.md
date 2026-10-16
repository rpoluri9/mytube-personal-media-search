# MyTube — Personal Media Search Platform

A YouTube-like **personal media library** built with **Java 17 + Spring Boot 3**:
browse a catalog of movies, songs, stories and videos, search it by text, and —
the key feature — **search by image**: upload a screenshot or any picture
(straight from your phone gallery) and MyTube finds the matching item in your
library and shows everything it knows about it — songs, story/plot, cast and
related media.

No AI/ML libraries are used anywhere. Image matching is pure-Java perceptual
hashing (dHash).

## Features

- **Media catalog** — 16 seeded items: 4 movies × 2 songs each, 2 short stories,
  2 videos. Each item has title, type, description, tags, creator, year,
  duration, plot summary and a generated thumbnail.
- **Related-items graph** — a movie links to its songs and to the story behind
  its plot (e.g. *RRR* ↔ *Naatu Naatu*, *Etthara Jenda* ↔ *The Blue Jackal*).
- **Text search** — `GET /api/search?q=` matches title/description/tags/creator
  (case-insensitive), title hits ranked first.
- **Image search** — `POST /api/search/image` (multipart `file`): computes the
  perceptual hash of the upload, finds the closest library thumbnail by Hamming
  distance, and returns the matched item **with its full related data**.
- **Web UI (Thymeleaf)** — YouTube-style dark theme, mobile-friendly: home grid,
  detail pages with song/story/related cards, header search bar, and an
  **📷 Image search** button (`accept="image/*"` so phones open the gallery).
- **File-based H2** — the library persists in `./data/mytube` across restarts;
  thumbnails live in `./data/thumbnails/`.

## How image search works (honest scope)

1. The upload is decoded with `ImageIO` (PNG/JPEG/…).
2. `DHashUtil` shrinks it to 9×8 grayscale and builds a 64-bit **dHash**:
   one bit per adjacent pixel pair (left brighter than right?).
3. `LocalHashMatchProvider` compares that hash against every catalog
   thumbnail's stored dHash and picks the smallest **Hamming distance**.
4. Distance ≤ **10** → match. Otherwise → `match: null` with a friendly message.

**Scope, stated plainly:** this matches against **your personal library**
(16 items here) using perceptual hashing — it recognises a screenshot or
resized copy of a library thumbnail, even after recompression. It is **not**
a web-scale reverse-image search; it cannot identify arbitrary pictures from
the internet. The `ImageMatchProvider` interface is the extension point for
that (see below).

### How to extend with a web-scale reverse-image API (e.g. SerpApi)

1. Add the SerpApi client dependency and API key to
   `src/main/resources/application.properties`.
2. Create `com.mytube.service.image.SerpApiMatchProvider implements ImageMatchProvider`
   that uploads/queries the image and maps the top organic result back to a
   `MediaItem` (or returns the web result directly).
3. Annotate it `@Primary` — `ImageSearchController` depends only on the
   interface, so **no controller changes are needed**.

## Run it

Prerequisites: Java 17+, Maven.

```bash
cd project-10-mytube
mvn package -DskipTests
java -jar target/mytube-1.0.0.jar
```

Open http://localhost:8080 — the library seeds itself on first boot
(16 items + thumbnails + hashes).

Try the APIs:

```bash
# text search
curl "http://localhost:8080/api/search?q=naatu"

# one item with related data
curl http://localhost:8080/api/media/1

# image search: upload any library thumbnail (distance 0 -> exact match)
curl -X POST http://localhost:8080/api/search/image \
  -F "file=@./data/thumbnails/naatu-naatu.png"
```

## API reference

| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Home page (grid); `?q=` filters by text |
| GET | `/media/{id}` | Detail page: songs, story/plot, related media |
| POST | `/search/image` | Web image-search form → results page |
| GET | `/api/media` | All items (JSON, with related items) |
| GET | `/api/media/{id}` | One item + related items (404 if unknown) |
| GET | `/api/search?q=` | Text search, title hits first (400 if `q` missing) |
| POST | `/api/search/image` | Multipart `file` → `{match, hammingDistance, message}` |

`POST /api/search/image` responses:

- **200 match** — `{"match": {…full MediaItem incl. relatedItems…}, "hammingDistance": 3, "message": "Matched 'RRR' (perceptual-hash distance 3)."}`
- **200 no match** — `{"match": null, "hammingDistance": 27, "message": "No close match found…"}`
- **400** — empty upload or undecodable file, with a clear message.

## Project structure

```
src/main/java/com/mytube/
├── MyTubeApplication.java
├── model/          MediaItem (JPA entity), MediaType (VIDEO/SONG/MOVIE/STORY)
├── repo/           MediaItemRepository (Spring Data JPA)
├── service/
│   ├── MediaService.java          text search + ranking
│   ├── ThumbnailService.java      Java2D thumbnail generation
│   └── image/
│       ├── DHashUtil.java              pure-Java dHash + Hamming distance
│       ├── ImageMatchProvider.java     strategy interface (extension point)
│       ├── LocalHashMatchProvider.java hash matching vs the library
│       └── ImageSearchResult.java
├── seed/           DataSeeder (16 items, thumbnails, hashes, relations)
├── web/            HomeController, MediaApiController,
│                   ImageSearchController, ImageSearchResponse
└── config/         WebConfig (serves ./data/thumbnails)
src/main/resources/templates/   index.html, detail.html, image-result.html
docs/   architecture.png, data-model.png, image-search-flow.png
```

## Tech stack

Java 17 · Spring Boot 3.2 · Spring Data JPA · H2 (file) · Thymeleaf ·
JUnit 5 · MockMvc · Java2D · Maven

## Tests

30 tests, all passing (`mvn test`): dHash unit tests (identical → 0,
different → far, resized copy → close), search ranking tests, repository
tests, seed-data tests, and MockMvc tests including multipart upload of a
real generated thumbnail asserting the correct match.
