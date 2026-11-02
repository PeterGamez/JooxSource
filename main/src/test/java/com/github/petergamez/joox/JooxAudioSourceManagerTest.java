package com.github.petergamez.joox;

import com.github.petergamez.joox.config.JooxConfig;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JooxAudioSourceManagerTest {

    @Test
    void testSourceName() {
        JooxAudioSourceManager manager = new JooxAudioSourceManager();
        assertEquals("joox", manager.getSourceName());
    }

    @Test
    void testDisabledManagerReturnsNull() {
        JooxConfig config = new JooxConfig();
        config.setEnabled(false);

        JooxAudioSourceManager manager = new JooxAudioSourceManager(config);
        assertNull(manager.loadItem(null, new AudioReference("https://www.joox.com/id/single/abc12345", null)));
    }

    @Test
    void testUnsupportedUrlReturnsNull() {
        JooxAudioSourceManager manager = new JooxAudioSourceManager();
        assertNull(manager.loadItem(null, new AudioReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null)));
        assertNull(manager.loadItem(null, new AudioReference("https://open.spotify.com/track/123", null)));
    }
}

