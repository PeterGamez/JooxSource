package com.github.petergamez.joox;

import com.github.petergamez.joox.api.JooxApiClient;
import com.github.petergamez.joox.config.JooxConfig;
import com.github.petergamez.joox.proxy.ProxyRotator;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main AudioSourceManager for JOOX, resolving tracks, albums, playlists, and search queries,
 * with hybrid Next.js web scraping and fallback mirroring.
 */
public class JooxAudioSourceManager implements AudioSourceManager, HttpConfigurable {

    private static final Logger log = LoggerFactory.getLogger(JooxAudioSourceManager.class);

    private static final String SEARCH_PREFIX_FULL = "jooxsearch:";
    private static final String SEARCH_PREFIX_SHORT = "jxsearch:";

    private static final Pattern TRACK_URL_PATTERN = Pattern.compile(
            "^https?://(?:www\\.)?joox\\.com/(?:[a-zA-Z]{2}/)?(?:single|track)/([a-zA-Z0-9_+=-]+)"
    );
    private static final Pattern ALBUM_URL_PATTERN = Pattern.compile(
            "^https?://(?:www\\.)?joox\\.com/(?:[a-zA-Z]{2}/)?album/([a-zA-Z0-9_+=-]+)"
    );
    private static final Pattern PLAYLIST_URL_PATTERN = Pattern.compile(
            "^https?://(?:www\\.)?joox\\.com/(?:[a-zA-Z]{2}/)?playlist/([a-zA-Z0-9_+=-]+)"
    );

    private static final Pattern DIRECT_TRACK_PATTERN = Pattern.compile("^joox:track:([a-zA-Z0-9_+=-]+)$");
    private static final Pattern DIRECT_ALBUM_PATTERN = Pattern.compile("^joox:album:([a-zA-Z0-9_+=-]+)$");
    private static final Pattern DIRECT_PLAYLIST_PATTERN = Pattern.compile("^joox:playlist:([a-zA-Z0-9_+=-]+)$");

    private final JooxConfig config;
    private final ProxyRotator proxyRotator;
    private final JooxApiClient apiClient;
    private final HttpInterfaceManager httpInterfaceManager;
    private AudioPlayerManager audioPlayerManager;

    public JooxAudioSourceManager() {
        this(new JooxConfig());
    }

    public JooxAudioSourceManager(JooxConfig config) {
        this.config = config != null ? config : new JooxConfig();
        this.proxyRotator = new ProxyRotator(this.config.getProxy());
        this.apiClient = new JooxApiClient(this.config, this.proxyRotator);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "joox";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (!config.isEnabled()) {
            return null;
        }

        if (this.audioPlayerManager == null && manager != null) {
            this.audioPlayerManager = manager;
        }

        String identifier = reference.identifier;
        if (identifier == null) {
            return null;
        }

        try {
            // 1. Search Query Handling
            if (config.isAllowSearch()) {
                if (identifier.startsWith(SEARCH_PREFIX_FULL)) {
                    String query = identifier.substring(SEARCH_PREFIX_FULL.length()).trim();
                    return apiClient.searchTracks(query, this::buildAudioTrack);
                } else if (identifier.startsWith(SEARCH_PREFIX_SHORT)) {
                    String query = identifier.substring(SEARCH_PREFIX_SHORT.length()).trim();
                    return apiClient.searchTracks(query, this::buildAudioTrack);
                }
            }

            // 2. Direct Single Track URL / Pattern
            Matcher trackMatcher = TRACK_URL_PATTERN.matcher(identifier);
            if (trackMatcher.find()) {
                String songId = trackMatcher.group(1);
                AudioTrackInfo trackInfo = apiClient.fetchTrackInfo(songId);
                return buildAudioTrack(trackInfo);
            }
            Matcher directTrackMatcher = DIRECT_TRACK_PATTERN.matcher(identifier);
            if (directTrackMatcher.matches()) {
                String songId = directTrackMatcher.group(1);
                AudioTrackInfo trackInfo = apiClient.fetchTrackInfo(songId);
                return buildAudioTrack(trackInfo);
            }

            // 3. Album URL / Pattern
            Matcher albumMatcher = ALBUM_URL_PATTERN.matcher(identifier);
            if (albumMatcher.find()) {
                String albumId = albumMatcher.group(1);
                return apiClient.fetchAlbum(albumId, this::buildAudioTrack);
            }
            Matcher directAlbumMatcher = DIRECT_ALBUM_PATTERN.matcher(identifier);
            if (directAlbumMatcher.matches()) {
                String albumId = directAlbumMatcher.group(1);
                return apiClient.fetchAlbum(albumId, this::buildAudioTrack);
            }

            // 4. Playlist URL / Pattern
            Matcher playlistMatcher = PLAYLIST_URL_PATTERN.matcher(identifier);
            if (playlistMatcher.find()) {
                String playlistId = playlistMatcher.group(1);
                return apiClient.fetchPlaylist(playlistId, this::buildAudioTrack);
            }
            Matcher directPlaylistMatcher = DIRECT_PLAYLIST_PATTERN.matcher(identifier);
            if (directPlaylistMatcher.matches()) {
                String playlistId = directPlaylistMatcher.group(1);
                return apiClient.fetchPlaylist(playlistId, this::buildAudioTrack);
            }

        } catch (Exception e) {
            log.error("Failed to load JOOX audio item for identifier '{}': {}", identifier, e.getMessage(), e);
        }

        return null;
    }

    public AudioTrack buildAudioTrack(AudioTrackInfo trackInfo) {
        return new JooxAudioTrack(trackInfo, this);
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    public JooxApiClient getApiClient() {
        return apiClient;
    }

    public JooxConfig getConfig() {
        return config;
    }

    public ProxyRotator getProxyRotator() {
        return proxyRotator;
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return audioPlayerManager;
    }

    public void setAudioPlayerManager(AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // AudioTrackInfo encapsulates necessary state; no additional payload required
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new JooxAudioTrack(trackInfo, this);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Error shutting down JOOX HTTP interface manager", e);
        }
    }
}
