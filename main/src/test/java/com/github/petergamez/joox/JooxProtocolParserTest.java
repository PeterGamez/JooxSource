package com.github.petergamez.joox;

import com.github.petergamez.joox.api.JooxProtocolParser;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JooxProtocolParserTest {

    private final JooxProtocolParser parser = new JooxProtocolParser();

    @Test
    void testParseSingleTrack() throws IOException {
        String json = "{\n" +
                "  \"songid\": \"song_12345\",\n" +
                "  \"song_name\": \"Hati-Hati di Jalan\",\n" +
                "  \"singer_name\": \"Tulus\",\n" +
                "  \"play_duration\": 242,\n" +
                "  \"album_pic\": \"https://img.joox.com/pic/123.jpg\"\n" +
                "}";

        AudioTrackInfo info = parser.parseTrack(json, "fallback_id");

        assertEquals("song_12345", info.identifier);
        assertEquals("Hati-Hati di Jalan", info.title);
        assertEquals("Tulus", info.author);
        assertEquals(242000L, info.length);
        assertEquals("https://img.joox.com/pic/123.jpg", info.artworkUrl);
    }

    @Test
    void testParseSearchResults() throws IOException {
        String json = "{\n" +
                "  \"itemlist\": [\n" +
                "    {\n" +
                "      \"songid\": \"s1\",\n" +
                "      \"song_name\": \"Song One\",\n" +
                "      \"singer_name\": \"Artist A\",\n" +
                "      \"play_duration\": 180\n" +
                "    },\n" +
                "    {\n" +
                "      \"songid\": \"s2\",\n" +
                "      \"song_name\": \"Song Two\",\n" +
                "      \"singer_name\": \"Artist B\",\n" +
                "      \"play_duration\": 200\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        AudioPlaylist playlist = parser.parseSearch(json, info -> new DummyAudioTrack(info));

        assertNotNull(playlist);
        assertTrue(playlist.isSearchResult());
        assertEquals(2, playlist.getTracks().size());
        assertEquals("Song One", playlist.getTracks().get(0).getInfo().title);
        assertEquals("Song Two", playlist.getTracks().get(1).getInfo().title);
    }

    @Test
    void testParsePlaybackUrlWithQualityPreference() throws IOException {
        String json = "{\n" +
                "  \"code\": 0,\n" +
                "  \"r320Url\": \"https://stream.joox.com/cdn/audio_320k.mp3\",\n" +
                "  \"flacUrl\": \"https://stream.joox.com/cdn/audio_lossless.flac\",\n" +
                "  \"m4aUrl\": \"https://stream.joox.com/cdn/audio_96k.m4a\"\n" +
                "}";

        // When FLAC is preferred first
        String flacUrl = parser.parsePlaybackUrl(json, java.util.List.of(
                com.github.petergamez.joox.config.JooxAudioQuality.FLAC,
                com.github.petergamez.joox.config.JooxAudioQuality.MP3_320
        ));
        assertEquals("https://stream.joox.com/cdn/audio_lossless.flac", flacUrl);

        // When MP3_320 is preferred first
        String mp3Url = parser.parsePlaybackUrl(json, java.util.List.of(
                com.github.petergamez.joox.config.JooxAudioQuality.MP3_320,
                com.github.petergamez.joox.config.JooxAudioQuality.FLAC
        ));
        assertEquals("https://stream.joox.com/cdn/audio_320k.mp3", mp3Url);

        // When low bitrate M4A_96 is preferred
        String m4aUrl = parser.parsePlaybackUrl(json, java.util.List.of(
                com.github.petergamez.joox.config.JooxAudioQuality.M4A_96,
                com.github.petergamez.joox.config.JooxAudioQuality.MP3_320
        ));
        assertEquals("https://stream.joox.com/cdn/audio_96k.m4a", m4aUrl);
    }

    @Test
    void testParsePlaybackUrlFallbackWhenPreferredUnavailable() throws IOException {
        // Only 96k AAC available
        String json = "{\n" +
                "  \"code\": 0,\n" +
                "  \"m4aUrl\": \"https://stream.joox.com/cdn/audio_96k.m4a\"\n" +
                "}";

        // Preferred is 320k, then 128k, then 96k
        String url = parser.parsePlaybackUrl(json, java.util.List.of(
                com.github.petergamez.joox.config.JooxAudioQuality.MP3_320,
                com.github.petergamez.joox.config.JooxAudioQuality.MP3_128,
                com.github.petergamez.joox.config.JooxAudioQuality.M4A_96
        ));
        assertEquals("https://stream.joox.com/cdn/audio_96k.m4a", url);
    }

    private static class DummyAudioTrack extends com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack {
        public DummyAudioTrack(AudioTrackInfo trackInfo) {
            super(trackInfo);
        }

        @Override
        public void process(com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor executor) {
        }

        @Override
        protected AudioTrack makeShallowClone() {
            return new DummyAudioTrack(trackInfo);
        }

        @Override
        public com.sedmelluq.discord.lavaplayer.source.AudioSourceManager getSourceManager() {
            return null;
        }
    }
}

