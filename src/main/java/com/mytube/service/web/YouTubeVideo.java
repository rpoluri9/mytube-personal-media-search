package com.mytube.service.web;

/** One YouTube video from a Data API v3 search. */
public record YouTubeVideo(
        String videoId,
        String title,
        String channelTitle,
        String channelId,
        String videoUrl,
        String channelUrl,
        String description,
        String thumbnailUrl) {

    public static YouTubeVideo of(String videoId, String title, String channelTitle,
                                  String channelId, String description, String thumbnailUrl) {
        return new YouTubeVideo(
                videoId,
                title,
                channelTitle,
                channelId,
                "https://www.youtube.com/watch?v=" + videoId,
                "https://www.youtube.com/channel/" + channelId,
                description,
                thumbnailUrl);
    }
}
