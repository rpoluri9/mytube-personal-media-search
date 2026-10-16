package com.mytube.seed;

import com.mytube.model.MediaItem;
import com.mytube.model.MediaType;
import com.mytube.repo.MediaItemRepository;
import com.mytube.service.ThumbnailService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the personal library on every boot: 16 items (4 movies x 2 songs,
 * 2 stories, 2 videos), generates their thumbnails with Java2D, computes the
 * dHash of each, and wires the related-items graph.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final MediaItemRepository repository;
    private final ThumbnailService thumbnails;

    public DataSeeder(MediaItemRepository repository, ThumbnailService thumbnails) {
        this.repository = repository;
        this.thumbnails = thumbnails;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        List<MediaItem> items = buildCatalog();

        for (MediaItem item : items) {
            String filename = slug(item.getTitle()) + ".png";
            long hash = thumbnails.generateThumbnail(filename, item.getTitle(), item.getType().name());
            item.setThumbnailFilename(filename);
            item.setImageHash(hash);
        }
        repository.saveAll(items);
        System.out.println("MyTube: seeded " + items.size() + " media items.");
    }

    private List<MediaItem> buildCatalog() {
        List<MediaItem> items = new ArrayList<>();

        // ---------- Movies + their songs ----------
        MediaItem rrr = movie("RRR", "S. S. Rajamouli", 2022, 10920,
                "Epic period action drama about two revolutionaries — Alluri Sitarama Raju and "
                        + "Komaram Bheem — who forge an unlikely friendship while fighting British rule.",
                "action,drama,telugu,period,blockbuster",
                "In 1920s colonial India, officer Rama Raju and tribal protector Bheem cross paths. "
                        + "Their friendship is tested when duty and rebellion collide, leading to a "
                        + "fiery uprising against the British Raj.");
        MediaItem naatu = song("Naatu Naatu", "M. M. Keeravani", 2022, 222,
                "Oscar-winning dance number from RRR; music by M. M. Keeravani, lyrics by Chandrabose.",
                "song,telugu,rrr,soundtrack,oscar,dance");
        MediaItem etthara = song("Etthara Jenda", "M. M. Keeravani", 2022, 270,
                "Rousing anthem from RRR about raising the flag of freedom.",
                "song,telugu,rrr,soundtrack,anthem");
        rrr.addRelated(naatu);
        rrr.addRelated(etthara);

        MediaItem idiots = movie("3 Idiots", "Rajkumar Hirani", 2009, 10020,
                "Comedy-drama following three engineering students and their friend Rancho, "
                        + "who challenges the pressure-cooker education system.",
                "comedy,drama,hindi,college,classic",
                "Farhan and Raju search for their long-lost friend Rancho, reliving their "
                        + "college days under the tyrannical Professor Viru Sahastrabuddhe — "
                        + "and learning what success really means.");
        MediaItem allIzz = song("All Izz Well", "Shantanu Moitra", 2009, 274,
                "The iconic feel-good anthem from 3 Idiots.",
                "song,hindi,3 idiots,soundtrack,anthem");
        MediaItem sunshine = song("Give Me Some Sunshine", "Shantanu Moitra", 2009, 293,
                "Gentle, nostalgic song from 3 Idiots about holding on to hope.",
                "song,hindi,3 idiots,soundtrack,melody");
        idiots.addRelated(allIzz);
        idiots.addRelated(sunshine);

        MediaItem dune = movie("Dune: Part Two", "Denis Villeneuve", 2024, 9960,
                "Paul Atreides unites with the Fremen and wages war against House Harkonnen "
                        + "as visions of a holy war haunt him.",
                "sci-fi,adventure,epic,desert",
                "Paul embraces his destiny among the Fremen of Arrakis, torn between the love "
                        + "of his life and the fate of the known universe, as he walks the path "
                        + "toward a war fought in his name.");
        MediaItem quiet = song("A Time of Quiet Between the Storms", "Hans Zimmer", 2024, 362,
                "Sweeping score cue from the Dune: Part Two soundtrack by Hans Zimmer.",
                "score,dune,soundtrack,hans zimmer,orchestral");
        MediaItem ring = song("Kiss the Ring", "Hans Zimmer", 2024, 302,
                "Driving action cue from the Dune: Part Two soundtrack by Hans Zimmer.",
                "score,dune,soundtrack,hans zimmer,orchestral");
        dune.addRelated(quiet);
        dune.addRelated(ring);

        MediaItem spidey = movie("Spider-Man: No Way Home", "Jon Watts", 2021, 8880,
                "Peter Parker's identity is exposed; a spell gone wrong tears open the "
                        + "multiverse and villains from other realities pour in.",
                "superhero,action,marvel,multiverse",
                "When a spell to restore his secret identity fractures the multiverse, Peter "
                        + "must face villains from every universe — and learn what it truly "
                        + "means to be Spider-Man.");
        MediaItem zimbra = song("I Zimbra", "Talking Heads", 1980, 181,
                "Energetic Talking Heads track featured in Spider-Man: No Way Home.",
                "song,rock,spider-man,soundtrack,talking heads");
        MediaItem suite = song("No Way Home Suite", "Michael Giacchino", 2021, 652,
                "Orchestral suite from the Spider-Man: No Way Home score by Michael Giacchino.",
                "score,spider-man,soundtrack,giacchino,orchestral");
        spidey.addRelated(zimbra);
        spidey.addRelated(suite);

        // ---------- Stories (linked from movies as "the tale behind the plot") ----------
        MediaItem jackal = story("The Blue Jackal",
                "Panchatantra (Vishnu Sharma)",
                "A jackal falls into a vat of blue dye and convinces the jungle he is a "
                        + "divine king — until one howl at the moon gives him away.",
                "story,fable,panchatantra,moral,classic");
        MediaItem crow = story("The Thirsty Crow",
                "Panchatantra",
                "A thirsty crow drops pebbles into a pitcher, raising the water level sip by "
                        + "sip — wit wins where strength cannot.",
                "story,fable,panchatantra,moral,classic");
        rrr.addRelated(jackal);      // both are Indian folk-rooted tales
        idiots.addRelated(crow);     // wit over rote, like Rancho
        dune.addRelated(jackal);
        spidey.addRelated(crow);

        // ---------- Videos ----------
        MediaItem spring = video("Spring Boot Crash Course", "MyTube Originals", 2025, 5400,
                "Full hands-on walkthrough: REST APIs, Spring Data JPA, Thymeleaf and "
                        + "testing a Spring Boot 3 application from scratch.",
                "tutorial,spring boot,java,programming,backend");
        MediaItem food = video("Hyderabad Street Food Vlog", "MyTube Originals", 2025, 1200,
                "A tasty evening tour: Irani chai, biryani lanes, and midnight haleem "
                        + "in the old city of Hyderabad.",
                "vlog,food,hyderabad,travel,street food");

        items.add(rrr);
        items.add(naatu);
        items.add(etthara);
        items.add(idiots);
        items.add(allIzz);
        items.add(sunshine);
        items.add(dune);
        items.add(quiet);
        items.add(ring);
        items.add(spidey);
        items.add(zimbra);
        items.add(suite);
        items.add(jackal);
        items.add(crow);
        items.add(spring);
        items.add(food);
        return items;
    }

    private MediaItem movie(String title, String creator, int year, int durationSec,
                            String description, String tags, String plotSummary) {
        MediaItem m = new MediaItem(title, MediaType.MOVIE);
        m.setCreator(creator);
        m.setYear(year);
        m.setDurationSec(durationSec);
        m.setDescription(description);
        m.setTags(tags);
        m.setPlotSummary(plotSummary);
        return m;
    }

    private MediaItem song(String title, String creator, int year, int durationSec,
                           String description, String tags) {
        MediaItem m = new MediaItem(title, MediaType.SONG);
        m.setCreator(creator);
        m.setYear(year);
        m.setDurationSec(durationSec);
        m.setDescription(description);
        m.setTags(tags);
        return m;
    }

    private MediaItem story(String title, String creator, String plotSummary, String tags) {
        MediaItem m = new MediaItem(title, MediaType.STORY);
        m.setCreator(creator);
        m.setDescription(plotSummary);
        m.setPlotSummary(plotSummary);
        m.setTags(tags);
        return m;
    }

    private MediaItem video(String title, String creator, int year, int durationSec,
                            String description, String tags) {
        MediaItem m = new MediaItem(title, MediaType.VIDEO);
        m.setCreator(creator);
        m.setYear(year);
        m.setDurationSec(durationSec);
        m.setDescription(description);
        m.setTags(tags);
        return m;
    }

    private String slug(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
