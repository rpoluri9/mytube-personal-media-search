package com.mytube.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * A single item in the personal media library: a video, song, movie or story.
 *
 * <p>Items are linked to each other through {@code relatedItems} — e.g. a movie
 * links to its songs and to the story its plot is based on. The relationship is
 * maintained bidirectionally by {@link #addRelated(MediaItem)}.
 *
 * <p>{@code @JsonIdentityInfo} keeps the REST API from recursing forever when
 * related items reference each other.
 */
@Entity
@Table(name = "media_item")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaType type;

    @Column(length = 2000)
    private String description;

    /** Comma-separated tags, e.g. "action,telugu,soundtrack". */
    private String tags;

    /** Artist / director / author / channel. */
    private String creator;

    @Column(name = "release_year")
    private Integer year;

    /** Duration in seconds (songs, videos, movies). */
    private Integer durationSec;

    /** File name of the generated thumbnail under ./data/thumbnails/. */
    private String thumbnailFilename;

    /** 64-bit perceptual hash (dHash) of the thumbnail, for image search. */
    private Long imageHash;

    /** Plot / story summary (movies, stories). */
    @Column(length = 4000)
    private String plotSummary;

    @ManyToMany
    @JoinTable(name = "media_related",
            joinColumns = @JoinColumn(name = "media_id"),
            inverseJoinColumns = @JoinColumn(name = "related_id"))
    @JsonIdentityReference(alwaysAsId = false)
    private Set<MediaItem> relatedItems = new HashSet<>();

    protected MediaItem() {
    }

    public MediaItem(String title, MediaType type) {
        this.title = title;
        this.type = type;
    }

    /** Links two items to each other (both directions). */
    public void addRelated(MediaItem other) {
        if (other == null || other == this) {
            return;
        }
        this.relatedItems.add(other);
        other.relatedItems.add(this);
    }

    /** Items related to this one, filtered by type (e.g. SONG, STORY). */
    public Set<MediaItem> relatedOfType(MediaType type) {
        Set<MediaItem> out = new HashSet<>();
        for (MediaItem m : relatedItems) {
            if (m.getType() == type) {
                out.add(m);
            }
        }
        return out;
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getThumbnailFilename() { return thumbnailFilename; }
    public void setThumbnailFilename(String thumbnailFilename) { this.thumbnailFilename = thumbnailFilename; }
    public Long getImageHash() { return imageHash; }
    public void setImageHash(Long imageHash) { this.imageHash = imageHash; }
    public String getPlotSummary() { return plotSummary; }
    public void setPlotSummary(String plotSummary) { this.plotSummary = plotSummary; }
    public Set<MediaItem> getRelatedItems() { return relatedItems; }
}
