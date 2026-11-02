package com.github.petergamez.joox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Lavaplayer AudioTrack implementation for JOOX tracks,
 * featuring automated Audio Fallback Mirroring (LavaSrc model).
 */
public class JooxAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(JooxAudioTrack.class);

    private final JooxAudioSourceManager sourceManager;

    public JooxAudioTrack(AudioTrackInfo trackInfo, JooxAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        String songId = this.trackInfo.identifier;

        // Automated Audio Fallback Mirroring (LavaSrc model)
        if (sourceManager.getConfig().getFallback().isEnabled()) {
            AudioPlayerManager apm = sourceManager.getAudioPlayerManager();
            if (apm != null) {
                List<String> providers = sourceManager.getConfig().getFallback().getProviders();
                String title = trackInfo.title != null ? trackInfo.title : "";
                String author = (trackInfo.author != null && !trackInfo.author.equalsIgnoreCase("Unknown Artist"))
                        ? trackInfo.author : "";
                String baseQuery = (title + " " + author).trim();
                if (baseQuery.isEmpty()) {
                    baseQuery = songId;
                }

                for (String provider : providers) {
                    if (provider == null || provider.trim().isEmpty()) {
                        continue;
                    }
                    String trimmed = provider.trim();
                    String searchQuery = trimmed;

                    boolean hasIsrc = searchQuery.contains("%ISRC%");
                    boolean hasQuery = searchQuery.contains("%QUERY%");

                    if (hasIsrc) {
                        if (trackInfo.isrc == null || trackInfo.isrc.trim().isEmpty()) {
                            // JOOX metadata does not contain ISRC codes. If provider only targets ISRC, skip to query provider.
                            if (!hasQuery) {
                                log.debug("Skipping ISRC fallback provider '{}' because track '{}' has no ISRC", provider, trackInfo.title);
                                continue;
                            }
                        } else {
                            searchQuery = searchQuery.replace("%ISRC%", trackInfo.isrc.trim());
                        }
                    }

                    if (hasQuery) {
                        searchQuery = searchQuery.replace("%QUERY%", baseQuery);
                    } else if (!hasIsrc) {
                        // Neither placeholder present: treat as prefix (e.g. "ytmsearch", "ytsearch:", "scsearch")
                        String prefix = trimmed.endsWith(":") ? trimmed : trimmed + ":";
                        searchQuery = prefix + baseQuery;
                    }

                    log.debug("Attempting fallback audio resolution for '{}' via '{}'", trackInfo.title, searchQuery);

                    try {
                        AudioItem item = apm.loadItemSync(searchQuery);
                        if (item instanceof AudioTrack track) {
                            if (track instanceof InternalAudioTrack internalTrack) {
                                log.info("Mirroring JOOX track '{}' via {} delegate [{}]", trackInfo.title, provider, track.getInfo().title);
                                processDelegate(internalTrack, executor);
                                return;
                            }
                        } else if (item instanceof AudioPlaylist playlist && !playlist.getTracks().isEmpty()) {
                            AudioTrack track = playlist.getSelectedTrack() != null
                                    ? playlist.getSelectedTrack()
                                    : playlist.getTracks().get(0);
                            if (track instanceof InternalAudioTrack internalTrack) {
                                log.info("Mirroring JOOX track '{}' via {} delegate playlist track [{}]", trackInfo.title, provider, track.getInfo().title);
                                processDelegate(internalTrack, executor);
                                return;
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Fallback provider '{}' failed for query '{}': {}", provider, searchQuery, ex.getMessage());
                    }
                }
            } else {
                log.warn("AudioPlayerManager is null; cannot execute fallback audio mirroring for '{}'", trackInfo.title);
            }
        }

        throw new IOException("Failed to play JOOX track '" + trackInfo.title + "' (" + songId + "): No fallback audio provider succeeded.");
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new JooxAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
