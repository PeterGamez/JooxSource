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

