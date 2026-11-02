package com.github.petergamez.joox.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.petergamez.joox.config.JooxAudioQuality;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Protocol parser for JOOX web and mobile API JSON responses.
 */
public class JooxProtocolParser {

    private static final Logger log = LoggerFactory.getLogger(JooxProtocolParser.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses a single track JSON response into AudioTrackInfo.
     */
    public AudioTrackInfo parseTrack(String json, String fallbackIdentifier) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode item = root.has("song_info") ? root.get("song_info") : root;

        String id = item.hasNonNull("songid") ? item.get("songid").asText() : fallbackIdentifier;
        String title = item.hasNonNull("song_name") ? item.get("song_name").asText() : "Unknown Track";
        String author = item.hasNonNull("singer_name") ? item.get("singer_name").asText() : "Unknown Artist";
        long duration = item.hasNonNull("play_duration") ? item.get("play_duration").asLong() * 1000L : 0L;
        String artworkUrl = item.hasNonNull("album_pic") ? item.get("album_pic").asText() : null;
        String uri = "https://www.joox.com/single/" + id;

        return new AudioTrackInfo(title, author, duration, id, false, uri, artworkUrl, null);
    }

    /**
     * Parses a search response into an AudioPlaylist marked as search result.
     */
    public AudioPlaylist parseSearch(String json, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonNode root = mapper.readTree(json);
        List<AudioTrack> tracks = new ArrayList<>();

        JsonNode items = root.path("itemlist");
        if (items.isMissingNode() || !items.isArray()) {
            items = root.path("tracks");
        }

        if (items.isArray()) {
            for (JsonNode item : items) {
                try {
                    String id = item.hasNonNull("songid") ? item.get("songid").asText() : "";
                    String title = item.hasNonNull("song_name") ? item.get("song_name").asText() : "Unknown Track";
                    String author = item.hasNonNull("singer_name") ? item.get("singer_name").asText() : "Unknown Artist";
                    long duration = item.hasNonNull("play_duration") ? item.get("play_duration").asLong() * 1000L : 0L;
                    String artworkUrl = item.hasNonNull("album_pic") ? item.get("album_pic").asText() : null;
                    String uri = "https://www.joox.com/single/" + id;

                    AudioTrackInfo info = new AudioTrackInfo(title, author, duration, id, false, uri, artworkUrl, null);
                    tracks.add(trackFactory.apply(info));
                } catch (Exception e) {
                    log.debug("Skipping malformed track item in search result: {}", e.getMessage());
                }
            }
        }

        return new BasicAudioPlaylist("JOOX Search Results", tracks, null, true);
    }

    /**
     * Parses an album or playlist response into an AudioPlaylist.
     */
    public AudioPlaylist parsePlaylist(String json, String defaultTitle, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonNode root = mapper.readTree(json);
        String name = root.hasNonNull("title") ? root.get("title").asText()
                : (root.hasNonNull("name") ? root.get("name").asText() : defaultTitle);

        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode items = root.path("songlist");
        if (items.isMissingNode() || !items.isArray()) {
            items = root.path("tracks");
        }

        if (items.isArray()) {
            for (JsonNode item : items) {
                try {
                    String id = item.hasNonNull("songid") ? item.get("songid").asText() : "";
                    String title = item.hasNonNull("song_name") ? item.get("song_name").asText() : "Unknown Track";
                    String author = item.hasNonNull("singer_name") ? item.get("singer_name").asText() : "Unknown Artist";
                    long duration = item.hasNonNull("play_duration") ? item.get("play_duration").asLong() * 1000L : 0L;
                    String artworkUrl = item.hasNonNull("album_pic") ? item.get("album_pic").asText() : null;
                    String uri = "https://www.joox.com/single/" + id;

                    AudioTrackInfo info = new AudioTrackInfo(title, author, duration, id, false, uri, artworkUrl, null);
                    tracks.add(trackFactory.apply(info));
                } catch (Exception e) {
                    log.debug("Skipping track item in playlist: {}", e.getMessage());
                }
            }
        }

        return new BasicAudioPlaylist(name, tracks, null, false);
    }

    /**
     * Extracts direct playable audio stream URL based on ordered quality preferences.
     */
    public String parsePlaybackUrl(String json, List<JooxAudioQuality> preferredQualities) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");

        if (preferredQualities != null && !preferredQualities.isEmpty()) {
            for (JooxAudioQuality quality : preferredQualities) {
                for (String key : quality.getCdnKeys()) {
                    if (root.hasNonNull(key) && !root.get(key).asText().isEmpty()) {
                        return root.get(key).asText();
                    }
                    if (data.isObject() && data.hasNonNull(key) && !data.get(key).asText().isEmpty()) {
                        return data.get(key).asText();
                    }
                }
            }
        }

        // Generic fallback URL candidates if none of the specific quality keys matched
        String[] genericKeys = {"playUrl", "url", "streamUrl", "mp3Url", "r320Url"};
        for (String key : genericKeys) {
            if (root.hasNonNull(key) && !root.get(key).asText().isEmpty()) {
                return root.get(key).asText();
            }
            if (data.isObject() && data.hasNonNull(key) && !data.get(key).asText().isEmpty()) {
                return data.get(key).asText();
            }
        }

        throw new IOException("No playable stream URL found in JOOX playback response");
    }

    /**
     * Default playback URL parser using standard quality preference order.
     */
    public String parsePlaybackUrl(String json) throws IOException {
        return parsePlaybackUrl(json, List.of(
                JooxAudioQuality.MP3_320,
                JooxAudioQuality.MP3_128,
                JooxAudioQuality.M4A_96,
                JooxAudioQuality.FLAC
        ));
    }
}
