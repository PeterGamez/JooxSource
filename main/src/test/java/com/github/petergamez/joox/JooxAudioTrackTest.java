package com.github.petergamez.joox;

import com.github.petergamez.joox.config.JooxConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JooxAudioTrackTest {

    @Test
    void testFallbackMirroringExecutesWhenDirectFails() throws Exception {
        JooxConfig config = new JooxConfig();
        config.setDirectStream(false); // Direct stream turned off
        config.getFallback().setEnabled(true);
        config.getFallback().setProviders(java.util.List.of("ytmsearch:"));

        JooxAudioSourceManager sourceManager = new JooxAudioSourceManager(config);

        AudioTrackInfo info = new AudioTrackInfo("ความรัก", "Bodyslam", 291000L, "qrc123", false, "https://www.joox.com/single/qrc123", null, null);
        JooxAudioTrack track = new JooxAudioTrack(info, sourceManager);

        AtomicBoolean delegateProcessed = new AtomicBoolean(false);
        DummyAudioTrack mockDelegate = new DummyAudioTrack(info, () -> delegateProcessed.set(true));

        // Mock AudioPlayerManager that returns mockDelegate
        AudioPlayerManager apm = new DefaultAudioPlayerManager() {
            @Override
            public AudioItem loadItemSync(String identifier) {
                if (identifier.equals("ytmsearch:ความรัก Bodyslam")) {
                    return mockDelegate;
                }
                return null;
            }
        };

        sourceManager.setAudioPlayerManager(apm);

        track.process(null);

        assertTrue(delegateProcessed.get(), "Fallback delegate track should have been executed");
    }

    @Test
    void testQueryTemplateSupport() throws Exception {
        JooxConfig config = new JooxConfig();
        config.setDirectStream(false);
        config.getFallback().setEnabled(true);
        config.getFallback().setProviders(java.util.List.of("ytmsearch:%QUERY%"));

        JooxAudioSourceManager sourceManager = new JooxAudioSourceManager(config);
        AudioTrackInfo info = new AudioTrackInfo("ความรัก", "Bodyslam", 291000L, "qrc123", false, "https://www.joox.com/single/qrc123", null, null);
        JooxAudioTrack track = new JooxAudioTrack(info, sourceManager);

        AtomicBoolean delegateProcessed = new AtomicBoolean(false);
        DummyAudioTrack mockDelegate = new DummyAudioTrack(info, () -> delegateProcessed.set(true));

        AudioPlayerManager apm = new DefaultAudioPlayerManager() {
            @Override
            public AudioItem loadItemSync(String identifier) {
                if (identifier.equals("ytmsearch:ความรัก Bodyslam")) {
                    return mockDelegate;
                }
                return null;
            }
        };

        sourceManager.setAudioPlayerManager(apm);
        track.process(null);

        assertTrue(delegateProcessed.get(), "Fallback with %QUERY% template should resolve properly");
    }

    @Test
    void testIsrcSkippedGracefullyWhenNullAndFallsBackToQuery() throws Exception {
        JooxConfig config = new JooxConfig();
        config.setDirectStream(false);
        config.getFallback().setEnabled(true);
        // User configures ISRC first (PulseLink style), followed by QUERY fallback
        config.getFallback().setProviders(java.util.List.of("ytsearch:\"%ISRC%\"", "ytmsearch:%QUERY%"));

        JooxAudioSourceManager sourceManager = new JooxAudioSourceManager(config);
        // Track with null ISRC (standard for JOOX metadata)
        AudioTrackInfo info = new AudioTrackInfo("ความรัก", "Bodyslam", 291000L, "qrc123", false, "https://www.joox.com/single/qrc123", null, null);
        JooxAudioTrack track = new JooxAudioTrack(info, sourceManager);

        AtomicBoolean delegateProcessed = new AtomicBoolean(false);
        DummyAudioTrack mockDelegate = new DummyAudioTrack(info, () -> delegateProcessed.set(true));

        AudioPlayerManager apm = new DefaultAudioPlayerManager() {
            @Override
            public AudioItem loadItemSync(String identifier) {
                if (identifier.equals("ytmsearch:ความรัก Bodyslam")) {
                    return mockDelegate;
                }
                return null;
            }
        };

        sourceManager.setAudioPlayerManager(apm);
        track.process(null);

        assertTrue(delegateProcessed.get(), "ISRC provider should be skipped without failing, falling back to %QUERY%");
    }

    @Test
    void testSelectedTrackFromPlaylistFallback() throws Exception {
        JooxConfig config = new JooxConfig();
        config.setDirectStream(false);
        config.getFallback().setEnabled(true);
        config.getFallback().setProviders(java.util.List.of("ytmsearch:%QUERY%"));

        JooxAudioSourceManager sourceManager = new JooxAudioSourceManager(config);
        AudioTrackInfo info = new AudioTrackInfo("ความรัก", "Bodyslam", 291000L, "qrc123", false, "https://www.joox.com/single/qrc123", null, null);
        JooxAudioTrack track = new JooxAudioTrack(info, sourceManager);

        AtomicBoolean delegateProcessed = new AtomicBoolean(false);
        DummyAudioTrack mockDelegate1 = new DummyAudioTrack(info, () -> {});
        DummyAudioTrack mockSelected = new DummyAudioTrack(info, () -> delegateProcessed.set(true));

        com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist playlist =
                new com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist("Search", java.util.List.of(mockDelegate1, mockSelected), mockSelected, true);

        AudioPlayerManager apm = new DefaultAudioPlayerManager() {
            @Override
            public AudioItem loadItemSync(String identifier) {
                return playlist;
            }
        };

        sourceManager.setAudioPlayerManager(apm);
        track.process(null);

        assertTrue(delegateProcessed.get(), "Should process selected track from playlist when available");
    }

    @Test
    void testThrowsExceptionWhenDirectAndFallbackBothFail() {
        JooxConfig config = new JooxConfig();
        config.setDirectStream(false);
        config.getFallback().setEnabled(false); // Fallback disabled

        JooxAudioSourceManager sourceManager = new JooxAudioSourceManager(config);
        AudioTrackInfo info = new AudioTrackInfo("NonExistent", "Nobody", 1000L, "none", false, "https://www.joox.com/single/none", null, null);
        JooxAudioTrack track = new JooxAudioTrack(info, sourceManager);

        assertThrows(IOException.class, () -> track.process(null));
    }

    private static class DummyAudioTrack extends com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack implements InternalAudioTrack {
        private final Runnable onProcess;

        public DummyAudioTrack(AudioTrackInfo trackInfo, Runnable onProcess) {
            super(trackInfo);
            this.onProcess = onProcess;
        }

        @Override
        public void process(LocalAudioTrackExecutor executor) {
            if (onProcess != null) {
                onProcess.run();
            }
        }

        @Override
        protected AudioTrack makeShallowClone() {
            return new DummyAudioTrack(trackInfo, onProcess);
        }

        @Override
        public com.sedmelluq.discord.lavaplayer.source.AudioSourceManager getSourceManager() {
            return null;
        }
    }
}

