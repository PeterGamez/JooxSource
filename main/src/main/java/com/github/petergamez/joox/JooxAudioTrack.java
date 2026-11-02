package com.github.petergamez.joox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Lavaplayer AudioTrack implementation for JOOX tracks.
 * Resolves the real-time audio stream CDN URL and probes the container format.
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
        log.debug("Resolving playback stream URL for JOOX song: {}", songId);

        String playbackUrl = sourceManager.getApiClient().resolvePlaybackUrl(
                songId,
                sourceManager.getConfig().getQualities()
        );

        if (playbackUrl == null || playbackUrl.isEmpty()) {
            throw new IOException("Failed to resolve playable CDN stream URL for song " + songId);
        }

        log.debug("Streaming audio for {} from CDN URL", songId);

        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                MediaContainerDetection detection = new MediaContainerDetection(
                        MediaContainerRegistry.DEFAULT_REGISTRY,
                        new AudioReference(playbackUrl, trackInfo.title),
                        stream,
                        MediaContainerHints.from(null, null)
                );

                MediaContainerDetectionResult result = detection.detectContainer();
                if (!result.isContainerDetected() || !result.isSupportedFile()) {
                    throw new IOException("Unable to probe media container for stream (Reason: " + result.getUnsupportedReason() + ")");
                }

                InternalAudioTrack delegateTrack = (InternalAudioTrack) result.getContainerDescriptor().createTrack(trackInfo, stream);
                processDelegate(delegateTrack, executor);
            }
        }
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

