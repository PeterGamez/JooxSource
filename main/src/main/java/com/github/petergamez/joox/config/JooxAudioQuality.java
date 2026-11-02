package com.github.petergamez.joox.config;

import java.util.List;

/**
 * Audio streaming quality options for JOOX streams with mapped CDN JSON response keys.
 */
public enum JooxAudioQuality {
    FLAC(1000, "lossless", "flac", List.of("flacUrl", "rFlacUrl", "losslessUrl")),
    MP3_320(320, "320k", "mp3", List.of("r320Url", "mp3320Url", "320Url")),
    MP3_128(128, "128k", "mp3", List.of("mp3Url", "r192Url", "r128Url", "128Url")),
    M4A_96(96, "96k", "m4a", List.of("m4aUrl", "r96Url", "96Url", "playUrl", "url"));

    private final int bitrate;
    private final String label;
    private final String extension;
    private final List<String> cdnKeys;

    JooxAudioQuality(int bitrate, String label, String extension, List<String> cdnKeys) {
        this.bitrate = bitrate;
        this.label = label;
        this.extension = extension;
        this.cdnKeys = cdnKeys;
    }

    public int getBitrate() {
        return bitrate;
    }

    public String getLabel() {
        return label;
    }

    public String getExtension() {
        return extension;
    }

    public List<String> getCdnKeys() {
        return cdnKeys;
    }

    public static JooxAudioQuality fromString(String value) {
        if (value == null) {
            return MP3_320;
        }
        for (JooxAudioQuality quality : values()) {
            if (quality.name().equalsIgnoreCase(value) || quality.label.equalsIgnoreCase(value)) {
                return quality;
            }
        }
        return MP3_320;
    }
}
