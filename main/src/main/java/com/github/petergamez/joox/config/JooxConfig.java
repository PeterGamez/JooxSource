package com.github.petergamez.joox.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure POJO configuration for JOOX Audio Source.
 * Free of Spring Boot dependencies to allow standalone usage in Lavaplayer.
 */
public class JooxConfig {

    private boolean enabled = true;
    private String countryCode = "TH"; // e.g. "TH", "ID", "MY", "HK"
    private List<JooxAudioQuality> qualities = new ArrayList<>(List.of(
            JooxAudioQuality.MP3_320,
            JooxAudioQuality.MP3_128,
            JooxAudioQuality.M4A_96,
            JooxAudioQuality.FLAC
    ));
    private String wmid = "";
    private String sessionToken = "";
    private boolean allowSearch = true;
    private int searchLimit = 20;
    private boolean directStream = false;
    private FallbackConfig fallback = new FallbackConfig();
    private ProxyConfig proxy = new ProxyConfig();

    public static class FallbackConfig {
        private boolean enabled = true;
        private List<String> providers = new ArrayList<>(List.of("ytmsearch:%QUERY%", "ytsearch:%QUERY%", "scsearch:%QUERY%"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getProviders() {
            return providers;
        }

        public void setProviders(List<String> providers) {
            if (providers != null && !providers.isEmpty()) {
                this.providers = new ArrayList<>(providers);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : "TH";
    }

    public List<JooxAudioQuality> getQualities() {
        return qualities;
    }

    public void setQualities(List<JooxAudioQuality> qualities) {
        if (qualities != null && !qualities.isEmpty()) {
            this.qualities = new ArrayList<>(qualities);
        }
    }

    /**
     * Backward-compatible getter returning the highest priority quality.
     */
    public JooxAudioQuality getQuality() {
        return (qualities != null && !qualities.isEmpty()) ? qualities.get(0) : JooxAudioQuality.MP3_320;
    }

    /**
     * Backward-compatible setter setting single preferred quality.
     */
    public void setQuality(JooxAudioQuality quality) {
        if (quality != null) {
            this.qualities = new ArrayList<>(List.of(quality));
        }
    }

    public String getWmid() {
        return wmid;
    }

    public void setWmid(String wmid) {
        this.wmid = wmid;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public boolean isAllowSearch() {
        return allowSearch;
    }

    public void setAllowSearch(boolean allowSearch) {
        this.allowSearch = allowSearch;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public boolean isDirectStream() {
        return directStream;
    }

    public void setDirectStream(boolean directStream) {
        this.directStream = directStream;
    }

    public FallbackConfig getFallback() {
        return fallback;
    }

    public void setFallback(FallbackConfig fallback) {
        this.fallback = fallback != null ? fallback : new FallbackConfig();
    }

    public ProxyConfig getProxy() {
        return proxy;
    }

    public void setProxy(ProxyConfig proxy) {
        this.proxy = proxy != null ? proxy : new ProxyConfig();
    }
}
