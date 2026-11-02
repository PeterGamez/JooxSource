package com.github.petergamez.joox;

import com.github.petergamez.joox.config.JooxAudioQuality;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JooxPluginLoaderTest {

    @Test
    void testPluginRegistrationWhenEnabled() {
        JooxPluginConfig config = new JooxPluginConfig();
        config.setEnabled(true);
        config.setCountryCode("ID");
        config.setQuality(JooxAudioQuality.MP3_320);

        JooxPluginLoader loader = new JooxPluginLoader(config);
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

        loader.configure(playerManager);

        assertNotNull(playerManager.source(JooxAudioSourceManager.class));
    }

    @Test
    void testPluginSkippedWhenDisabled() {
        JooxPluginConfig config = new JooxPluginConfig();
        config.setEnabled(false);

        JooxPluginLoader loader = new JooxPluginLoader(config);
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

        loader.configure(playerManager);

        assertNull(playerManager.source(JooxAudioSourceManager.class));
    }
}

