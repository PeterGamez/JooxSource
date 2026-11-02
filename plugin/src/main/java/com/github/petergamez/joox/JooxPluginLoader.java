package com.github.petergamez.joox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lavalink v4 SPI implementation registering JooxAudioSourceManager with AudioPlayerManager.
 */
@Service
public class JooxPluginLoader implements AudioPlayerManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JooxPluginLoader.class);

    private final JooxPluginConfig config;
    private final JooxAudioSourceManager sourceManager;

    public JooxPluginLoader(JooxPluginConfig config) {
        this.config = config;
        this.sourceManager = new JooxAudioSourceManager(config);
        log.info("JooxSource Plugin for Lavalink v4 initialized (Enabled: {}, Country: {}, Qualities: {}, REST API: {})",
                config.isEnabled(), config.getCountryCode(), config.getQualities(),
                config.getRestApi().isEnabled() ? "ENABLED" : "DISABLED");
    }

    @Override
    public AudioPlayerManager configure(AudioPlayerManager manager) {
        if (config.isEnabled()) {
            log.info("Registering JOOX AudioSourceManager to Lavalink AudioPlayerManager");
            manager.registerSourceManager(sourceManager);
        } else {
            log.info("JOOX AudioSourceManager registration skipped (disabled in config)");
        }
        return manager;
    }

    public JooxPluginConfig getConfig() {
        return config;
    }

    public JooxAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}

