package com.github.petergamez.joox.api;

import com.github.petergamez.joox.config.JooxAudioQuality;
import com.github.petergamez.joox.config.JooxConfig;
import com.github.petergamez.joox.proxy.ProxyEntry;
import com.github.petergamez.joox.proxy.ProxyRotator;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Built-in Java 17 HttpClient for communicating with JOOX web pages and API endpoints,
 * featuring Next.js __NEXT_DATA__ SSR page scraping, dynamic proxy rotation, and legacy failover resilience.
 */
public class JooxApiClient {

    private static final Logger log = LoggerFactory.getLogger(JooxApiClient.class);

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String WEB_BASE_URL = "https://www.joox.com";
    private static final String BASE_API_URL = "https://api.joox.com/web-fcgi-bin";
    private static final String OPENJOOX_API_URL = "https://api-jooxtt.sanook.com/openjoox/v1";

    private final JooxConfig config;
    private final ProxyRotator proxyRotator;
    private final JooxProtocolParser parser;
    private final HttpClient defaultHttpClient;

    public JooxApiClient(JooxConfig config, ProxyRotator proxyRotator) {
        this.config = config != null ? config : new JooxConfig();
        this.proxyRotator = proxyRotator != null ? proxyRotator : new ProxyRotator(this.config.getProxy());
        this.parser = new JooxProtocolParser();

        this.defaultHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(config.getProxy().getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * Executes an HTTP GET request with proxy rotation and failover retries using Java 17 HttpClient.
     */
    public String executeGet(URI uri) throws IOException {
        int maxAttempts = Math.max(1, config.getProxy().getRetryLimit());
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ProxyEntry proxyEntry = proxyRotator.nextProxy();
            HttpClient client = buildClientForProxy(proxyEntry);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", "https://www.joox.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,th;q=0.8");

            // Apply cookie authentication if configured
            StringBuilder cookieHeader = new StringBuilder();
            cookieHeader.append("country=").append(config.getCountryCode().toLowerCase()).append("; ");
            if (config.getWmid() != null && !config.getWmid().isEmpty()) {
                cookieHeader.append("wmid=").append(config.getWmid()).append("; ");
                cookieHeader.append("user_type=1; ");
            }
            if (config.getSessionToken() != null && !config.getSessionToken().isEmpty()) {
                cookieHeader.append("session_key=").append(config.getSessionToken()).append("; ");
            }
            requestBuilder.header("Cookie", cookieHeader.toString());

            try {
                HttpResponse<String> response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("JOOX returned HTTP status " + response.statusCode());
                }

                String content = response.body();
                if (content == null || content.isEmpty()) {
                    throw new IOException("JOOX returned empty response body");
                }

                if (proxyEntry != null) {
                    proxyRotator.markSuccess(proxyEntry);
                }
                return content;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("HTTP request interrupted for " + uri, e);
            } catch (Exception e) {
                lastException = e;
                if (proxyEntry != null) {
                    proxyRotator.markFailure(proxyEntry, e);
                }
                log.warn("Attempt {}/{} failed querying {} via proxy {}: {}",
                        attempt, maxAttempts, uri, proxyEntry != null ? proxyEntry : "DIRECT", e.getMessage());
            }
        }

        throw new IOException("Failed to execute JOOX request after " + maxAttempts + " attempts", lastException);
    }

    private HttpClient buildClientForProxy(ProxyEntry proxyEntry) {
        if (proxyEntry == null) {
            return defaultHttpClient;
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(config.getProxy().getConnectTimeoutSeconds()))
                .proxy(ProxySelector.of(proxyEntry.toInetSocketAddress()));

        if (proxyEntry.hasAuthentication()) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(
                                proxyEntry.getUsername(),
                                proxyEntry.getPassword().toCharArray()
                        );
                    }
                    return null;
                }
            });
        }

        return builder.build();
    }

    private static URI buildUri(String baseUrl, Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                first = false;
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                sb.append("=");
                sb.append(URLEncoder.encode(entry.getValue() != null ? entry.getValue() : "", StandardCharsets.UTF_8));
            }
        }
        return URI.create(sb.toString());
    }

    /**
     * Resolves metadata for a single JOOX track ID via Next.js SSR scraping with legacy API fallback.
     */
    public AudioTrackInfo fetchTrackInfo(String songId) throws IOException {
        String country = config.getCountryCode().toLowerCase();
        String encodedId = URLEncoder.encode(songId, StandardCharsets.UTF_8);
        URI webUri = URI.create(WEB_BASE_URL + "/" + country + "/single/" + encodedId);

        try {
            String html = executeGet(webUri);
            return parser.parseNextDataTrack(html, songId);
        } catch (Exception e) {
            log.debug("Next.js scraping failed for single track {}, falling back to legacy API: {}", songId, e.getMessage());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("songid", songId);
            params.put("country", country);
            params.put("lang", "en");

            URI apiUri = buildUri(BASE_API_URL + "/web_get_songinfo", params);
            String json = executeGet(apiUri);
            return parser.parseTrack(json, songId);
        }
    }

    /**
     * Searches JOOX for tracks matching the query via Next.js SSR scraping with legacy API fallback.
     */
    public AudioPlaylist searchTracks(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        String country = config.getCountryCode().toLowerCase();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        URI webUri = URI.create(WEB_BASE_URL + "/" + country + "/search/" + encodedQuery);

        try {
            String html = executeGet(webUri);
            return parser.parseNextDataSearch(html, trackFactory);
        } catch (Exception e) {
            log.debug("Next.js scraping failed for query '{}', falling back to legacy API: {}", query, e.getMessage());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("search_input", query);
            params.put("sin", "0");
            params.put("ein", String.valueOf(config.getSearchLimit()));
            params.put("country", country);
            params.put("lang", "en");

            URI apiUri = buildUri(BASE_API_URL + "/web_search", params);
            String json = executeGet(apiUri);
            return parser.parseSearch(json, trackFactory);
        }
    }

    /**
     * Fetches tracks from a JOOX album via Next.js SSR scraping with legacy API fallback.
     */
    public AudioPlaylist fetchAlbum(String albumId, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        String country = config.getCountryCode().toLowerCase();
        String encodedAlbumId = URLEncoder.encode(albumId, StandardCharsets.UTF_8);
        URI webUri = URI.create(WEB_BASE_URL + "/" + country + "/album/" + encodedAlbumId);

        try {
            String html = executeGet(webUri);
            return parser.parseNextDataAlbum(html, albumId, trackFactory);
        } catch (Exception e) {
            log.debug("Next.js scraping failed for album {}, falling back to legacy API: {}", albumId, e.getMessage());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("albumid", albumId);
            params.put("country", country);
            params.put("lang", "en");

            URI apiUri = buildUri(BASE_API_URL + "/web_get_albuminfo", params);
            String json = executeGet(apiUri);
            return parser.parsePlaylist(json, "JOOX Album " + albumId, trackFactory);
        }
    }

    /**
     * Fetches tracks from a JOOX playlist via Next.js SSR scraping with legacy API fallback.
     */
    public AudioPlaylist fetchPlaylist(String playlistId, Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        String country = config.getCountryCode().toLowerCase();
        String encodedPlaylistId = URLEncoder.encode(playlistId, StandardCharsets.UTF_8);
        URI webUri = URI.create(WEB_BASE_URL + "/" + country + "/playlist/" + encodedPlaylistId);

        try {
            String html = executeGet(webUri);
            return parser.parseNextDataPlaylist(html, playlistId, trackFactory);
        } catch (Exception e) {
            log.debug("Next.js scraping failed for playlist {}, falling back to legacy API: {}", playlistId, e.getMessage());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("playlistid", playlistId);
            params.put("country", country);
            params.put("lang", "en");

            URI apiUri = buildUri(BASE_API_URL + "/web_get_playlistinfo", params);
            String json = executeGet(apiUri);
            return parser.parsePlaylist(json, "JOOX Playlist " + playlistId, trackFactory);
        }
    }

    /**
     * Resolves playable CDN audio stream URL for a given track identifier based on ordered quality list.
     */
    public String resolvePlaybackUrl(String songId, List<JooxAudioQuality> qualities) throws IOException {
        List<JooxAudioQuality> targetQualities = (qualities != null && !qualities.isEmpty())
                ? qualities
                : config.getQualities();

        JooxAudioQuality topQuality = targetQualities.get(0);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("quality", topQuality.getLabel());
        params.put("country", config.getCountryCode().toLowerCase());

        URI uri = buildUri(OPENJOOX_API_URL + "/song/" + songId + "/play", params);

        try {
            String json = executeGet(uri);
            return parser.parsePlaybackUrl(json, targetQualities);
        } catch (Exception e) {
            log.debug("OpenJOOX playback resolution failed for {}, falling back to songinfo: {}", songId, e.getMessage());
            Map<String, String> fallbackParams = new LinkedHashMap<>();
            fallbackParams.put("songid", songId);
            fallbackParams.put("country", config.getCountryCode().toLowerCase());

            URI fallbackUri = buildUri(BASE_API_URL + "/web_get_songinfo", fallbackParams);
            String fallbackJson = executeGet(fallbackUri);
            return parser.parsePlaybackUrl(fallbackJson, targetQualities);
        }
    }

    public String resolvePlaybackUrl(String songId, JooxAudioQuality quality) throws IOException {
        return resolvePlaybackUrl(songId, quality != null ? List.of(quality) : config.getQualities());
    }

    public JooxConfig getConfig() {
        return config;
    }

    public ProxyRotator getProxyRotator() {
        return proxyRotator;
    }

    public JooxProtocolParser getParser() {
        return parser;
    }
}
