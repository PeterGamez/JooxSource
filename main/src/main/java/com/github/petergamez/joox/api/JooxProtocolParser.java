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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protocol parser for JOOX web and mobile API JSON responses,
 * including modern Next.js __NEXT_DATA__ SSR page scraping and legacy API fallbacks.
 */
public class JooxProtocolParser {

    private static final Logger log = LoggerFactory.getLogger(JooxProtocolParser.class);
    private static final Pattern NEXT_DATA_PATTERN = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extracts pageProps JsonNode from Next.js HTML response.
     */
    public JsonNode extractNextData(String html) throws IOException {
        if (html == null || html.isEmpty()) {
            throw new IOException("Empty HTML response received for JOOX page");
        }
        Matcher matcher = NEXT_DATA_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IOException("No __NEXT_DATA__ script block found in JOOX HTML response");
        }
        String json = matcher.group(1).trim();
        JsonNode root = mapper.readTree(json);
        return root.path("props").path("pageProps");
    }

    /**
     * Parses search results from Next.js HTML __NEXT_DATA__.
     */
    public AudioPlaylist parseNextDataSearch(String html, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonNode pageProps = extractNextData(html);
        List<AudioTrack> tracks = new ArrayList<>();

        JsonNode itemList = pageProps.path("searchSections").path("songs").path("item_list");
        if (!itemList.isArray() || itemList.isEmpty()) {
            itemList = pageProps.path("searchSections").path("bestMatch").path("item_list");
        }

        if (itemList.isArray()) {
            for (JsonNode item : itemList) {
                // An item can contain an array of song variations ("song") or directly "song_info"
                if (item.has("song") && item.get("song").isArray()) {
                    for (JsonNode songEntry : item.get("song")) {
                        JsonNode songInfo = songEntry.path("song_info");
                        if (!songInfo.isMissingNode() && !songInfo.isNull()) {
                            AudioTrackInfo trackInfo = parseSongInfoNode(songInfo, "");
                            if (trackInfo != null) {
                                tracks.add(trackFactory.apply(trackInfo));
                            }
                        }
                    }
                } else if (item.has("song_info")) {
                    JsonNode songInfo = item.get("song_info");
                    AudioTrackInfo trackInfo = parseSongInfoNode(songInfo, "");
                    if (trackInfo != null) {
                        tracks.add(trackFactory.apply(trackInfo));
                    }
                }
            }
        }

        return new BasicAudioPlaylist("JOOX Search Results", tracks, null, true);
    }

    /**
     * Parses a single track from Next.js HTML __NEXT_DATA__.
     */
    public AudioTrackInfo parseNextDataTrack(String html, String fallbackIdentifier) throws IOException {
        JsonNode pageProps = extractNextData(html);
        JsonNode singleInfo = pageProps.path("singleInfo");
        JsonNode playerSongData = pageProps.path("playerSongData");
        JsonNode passingArgs = pageProps.path("passingArgumentsData");

        String id = singleInfo.hasNonNull("id") ? singleInfo.get("id").asText()
                : (singleInfo.hasNonNull("encodeSongId") ? singleInfo.get("encodeSongId").asText()
                : (playerSongData.hasNonNull("id") ? playerSongData.get("id").asText() : fallbackIdentifier));

        String title = singleInfo.hasNonNull("title") ? singleInfo.get("title").asText()
                : (singleInfo.hasNonNull("name") ? singleInfo.get("name").asText()
                : (playerSongData.hasNonNull("name") ? playerSongData.get("name").asText() : "Unknown Track"));

        String author = singleInfo.hasNonNull("msinger") ? singleInfo.get("msinger").asText()
                : extractArtists(singleInfo.path("artistList"));
        if (author == null || author.isEmpty()) {
            author = extractArtists(playerSongData.path("artist"));
        }
        if (author == null || author.isEmpty()) {
            author = "Unknown Artist";
        }

        long duration = 0L;
        if (passingArgs.hasNonNull("play_duration")) {
            duration = passingArgs.get("play_duration").asLong() * 1000L;
        } else if (singleInfo.hasNonNull("play_duration")) {
            duration = singleInfo.get("play_duration").asLong() * 1000L;
        } else if (playerSongData.hasNonNull("duration")) {
            duration = playerSongData.get("duration").asLong() * 1000L;
        }

        String artworkUrl = singleInfo.hasNonNull("imgSrc") ? singleInfo.get("imgSrc").asText()
                : (playerSongData.hasNonNull("image") ? playerSongData.get("image").asText() : null);

        String uri = "https://www.joox.com/single/" + id;
        return new AudioTrackInfo(title, author, duration, id, false, uri, artworkUrl, null);
    }

    /**
     * Parses an album from Next.js HTML __NEXT_DATA__.
     */
    public AudioPlaylist parseNextDataAlbum(String html, String albumId, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonNode pageProps = extractNextData(html);
        JsonNode albumData = pageProps.path("albumData");
        JsonNode albumTrackData = pageProps.path("albumTrackData");

        String albumTitle = albumData.hasNonNull("title") ? albumData.get("title").asText()
                : (albumTrackData.hasNonNull("description") && !albumTrackData.get("description").asText().isEmpty()
                ? albumTrackData.get("description").asText() : "JOOX Album " + albumId);

        String albumCover = albumData.hasNonNull("imgSrc") ? albumData.get("imgSrc").asText() : null;

        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode items = albumData.path("trackList").path("items");
        if (!items.isArray() || items.isEmpty()) {
            items = albumTrackData.path("trackList");
        }

        if (items.isArray()) {
            for (JsonNode item : items) {
                AudioTrackInfo trackInfo = parseSongInfoNode(item, albumCover);
                if (trackInfo != null) {
                    tracks.add(trackFactory.apply(trackInfo));
                }
            }
        }

        return new BasicAudioPlaylist(albumTitle, tracks, null, false);
    }

    /**
     * Parses a playlist from Next.js HTML __NEXT_DATA__.
     */
    public AudioPlaylist parseNextDataPlaylist(String html, String playlistId, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        JsonNode pageProps = extractNextData(html);
        JsonNode playlistDetail = pageProps.path("playlistDetailList");
        JsonNode allTracks = pageProps.path("allPlaylistTracks");

        String playlistTitle = playlistDetail.hasNonNull("title") ? playlistDetail.get("title").asText()
                : (allTracks.hasNonNull("description") && !allTracks.get("description").asText().isEmpty()
                ? allTracks.get("description").asText() : "JOOX Playlist " + playlistId);

        String playlistCover = playlistDetail.hasNonNull("imgSrc") ? playlistDetail.get("imgSrc").asText() : null;

        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode items = playlistDetail.path("trackList");
        if (!items.isArray() || items.isEmpty()) {
            items = allTracks.path("trackList");
        }

        if (items.isArray()) {
            for (JsonNode item : items) {
                AudioTrackInfo trackInfo = parseSongInfoNode(item, playlistCover);
                if (trackInfo != null) {
                    tracks.add(trackFactory.apply(trackInfo));
                }
            }
        }

        return new BasicAudioPlaylist(playlistTitle, tracks, null, false);
    }

    private AudioTrackInfo parseSongInfoNode(JsonNode node, String fallbackArtwork) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String id = node.hasNonNull("id") ? node.get("id").asText()
                : (node.hasNonNull("songid") ? node.get("songid").asText() : "");

        String title = node.hasNonNull("name") ? node.get("name").asText()
                : (node.hasNonNull("song_name") ? node.get("song_name").asText() : "Unknown Track");

        String author = extractArtists(node.path("artist_list"));
        if (author == null || author.isEmpty()) {
            author = node.hasNonNull("singer_name") ? node.get("singer_name").asText() : "Unknown Artist";
        }

        long duration = 0L;
        if (node.hasNonNull("play_duration")) {
            duration = node.get("play_duration").asLong() * 1000L;
        }

        String artworkUrl = fallbackArtwork;
        JsonNode images = node.path("images");
        if (images.isArray() && !images.isEmpty()) {
            artworkUrl = images.get(0).path("url").asText(fallbackArtwork);
        } else if (node.hasNonNull("album_pic")) {
            artworkUrl = node.get("album_pic").asText();
        }

        String uri = "https://www.joox.com/single/" + id;
        return new AudioTrackInfo(title, author, duration, id, false, uri, artworkUrl, null);
    }

    private String extractArtists(JsonNode artistNode) {
        if (artistNode == null || !artistNode.isArray() || artistNode.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode a : artistNode) {
            String name = a.hasNonNull("name") ? a.get("name").asText() : "";
            if (!name.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(name);
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // Legacy API Parsers (Fallback)
    // =========================================================================

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
}
