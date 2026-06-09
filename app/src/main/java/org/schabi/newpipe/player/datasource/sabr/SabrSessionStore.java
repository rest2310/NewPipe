package org.schabi.newpipe.player.datasource.sabr;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.App;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrProbe;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class SabrSessionStore {
    private static final Map<String, Holder> SESSIONS = new ConcurrentHashMap<>();
    private static final Deque<String> ORDER = new ArrayDeque<>();
    private static final int MAX_SESSIONS = 2;
    private static volatile SabrWebViewPoTokenProvider sharedPoTokenProvider;

    private SabrSessionStore() {
    }

    @NonNull
    private static SabrWebViewPoTokenProvider provider() {
        SabrWebViewPoTokenProvider provider = sharedPoTokenProvider;
        if (provider == null) {
            synchronized (SabrSessionStore.class) {
                provider = sharedPoTokenProvider;
                if (provider == null) {
                    provider = new SabrWebViewPoTokenProvider(App.getInstance());
                    sharedPoTokenProvider = provider;
                }
            }
        }
        return provider;
    }

    public static final class Holder {
        @NonNull public final String videoId;
        @NonNull public final YoutubeSabrInfo info;
        @NonNull public final YoutubeSabrSession session;
        @NonNull public final YoutubeSabrFormat audioFormat;
        @NonNull public final YoutubeSabrFormat videoFormat;
        @Nullable private final SabrPoTokenProvider poTokenProvider;

        private volatile long playerTimeMs;
        private final Map<Integer, Long> readerPositions = new ConcurrentHashMap<>();
        private final AtomicBoolean tokenPrewarmStarted = new AtomicBoolean();
        @Nullable private volatile SabrStreamPump pump;

        Holder(@NonNull final String videoId,
               @NonNull final YoutubeSabrInfo info,
               @NonNull final YoutubeSabrSession session,
               @NonNull final YoutubeSabrFormat audioFormat,
               @NonNull final YoutubeSabrFormat videoFormat,
               @Nullable final SabrPoTokenProvider poTokenProvider) {
            this.videoId = videoId;
            this.info = info;
            this.session = session;
            this.audioFormat = audioFormat;
            this.videoFormat = videoFormat;
            this.poTokenProvider = poTokenProvider;
        }

        public long getPlayerTimeMs() {
            return playerTimeMs;
        }

        void setPlayerTimeMs(final long playerTimeMs) {
            this.playerTimeMs = playerTimeMs;
        }

        void setReaderPositionMs(final long ms) {
            setReaderPositionMs(audioFormat.getItag(), ms);
            setReaderPositionMs(videoFormat.getItag(), ms);
        }

        public void setReaderPositionMs(final int itag, final long ms) {
            readerPositions.put(itag, ms);
        }

        public long getReaderHeadMs() {
            long head = 0;
            final Long a = readerPositions.get(audioFormat.getItag());
            final Long v = readerPositions.get(videoFormat.getItag());
            if (a != null) {
                head = Math.max(head, a);
            }
            if (v != null) {
                head = Math.max(head, v);
            }
            return head;
        }

        public long getReaderTailMs() {
            final Long a = readerPositions.get(audioFormat.getItag());
            final Long v = readerPositions.get(videoFormat.getItag());
            if (a == null && v == null) {
                return 0;
            }
            if (a == null) {
                return v;
            }
            if (v == null) {
                return a;
            }
            return Math.min(a, v);
        }

        synchronized SabrStreamPump getPump(@NonNull final Localization localization) {
            if (pump == null) {
                pump = new SabrStreamPump(session, this, localization);
            }
            return pump;
        }

        boolean isBeyondEnd(@NonNull final SabrSegmentRequest request) {
            return session.isBeyondEnd(request);
        }

        void prewarmPoToken() {
            if (poTokenProvider == null || !tokenPrewarmStarted.compareAndSet(false, true)) {
                return;
            }
            final Thread warm = new Thread(() -> {
                try {
                    poTokenProvider.getPoToken(info, session.getStreamState());
                } catch (final Exception ignored) {
                    // Best effort; the pump mints on demand if this fails.
                }
            }, "SabrTokenPrewarm");
            warm.setDaemon(true);
            warm.start();
        }
    }

    public static void updatePlayerTime(@NonNull final String videoId, final long playerTimeMs) {
        final Holder holder = SESSIONS.get(videoId);
        if (holder != null && playerTimeMs >= 0) {
            holder.setPlayerTimeMs(playerTimeMs);
        }
    }

    @NonNull
    public static Holder getOrCreate(@NonNull final SabrPlaybackConfig config)
            throws IOException, ExtractionException {
        final Holder existing = SESSIONS.get(config.getVideoId());
        if (existing != null && sessionMatches(existing, config.getSelectedAudioItag(),
                config.getSelectedVideoItag())) {
            return existing;
        }

        synchronized (SabrSessionStore.class) {
            final Holder current = SESSIONS.get(config.getVideoId());
            if (current != null) {
                if (sessionMatches(current, config.getSelectedAudioItag(),
                        config.getSelectedVideoItag())) {
                    return current;
                }
                evict(config.getVideoId());
            }

            final SabrPoTokenProvider poTokenProvider = config.getPoTokenProvider() == null
                    ? null : provider();
            final YoutubeSabrInfo info = YoutubeSabrProbe.fetchSabrInfo(config.getVideoId(),
                    YoutubeSabrClientProfile.ANDROID, config.getLocalization(),
                    config.getContentCountry());
            final YoutubeSabrFormat audioFormat = pickAudioFormat(info,
                    config.getSelectedAudioItag());
            final YoutubeSabrFormat videoFormat = pickVideoFormat(info,
                    config.getSelectedVideoItag());
            if (audioFormat == null || videoFormat == null) {
                throw new IOException("SABR: could not select audio/video formats for "
                        + config.getVideoId());
            }

            final YoutubeSabrSession session = new YoutubeSabrSession(info, audioFormat,
                    videoFormat, poTokenProvider);
            final Holder holder = new Holder(config.getVideoId(), info, session, audioFormat,
                    videoFormat, poTokenProvider);
            SESSIONS.put(config.getVideoId(), holder);
            ORDER.remove(config.getVideoId());
            ORDER.addLast(config.getVideoId());
            while (ORDER.size() > MAX_SESSIONS) {
                final String old = ORDER.pollFirst();
                if (old != null && !old.equals(config.getVideoId())) {
                    evict(old);
                }
            }
            return holder;
        }
    }

    private static boolean sessionMatches(@NonNull final Holder holder,
                                          @Nullable final Integer selectedAudioItag,
                                          @Nullable final Integer selectedVideoItag) {
        if (selectedAudioItag != null && selectedAudioItag > 0
                && holder.audioFormat.getItag() != selectedAudioItag) {
            return false;
        }
        if (selectedVideoItag != null && selectedVideoItag > 0
                && holder.videoFormat.getItag() != selectedVideoItag) {
            return false;
        }
        return true;
    }

    @Nullable
    private static YoutubeSabrFormat pickAudioFormat(@NonNull final YoutubeSabrInfo info,
                                                     @Nullable final Integer selectedItag) {
        if (selectedItag != null && selectedItag > 0) {
            final YoutubeSabrFormat selected = info.findFormatByItag(selectedItag);
            if (selected != null && selected.isAudio()) {
                return selected;
            }
        }
        YoutubeSabrFormat aac = null;
        for (final YoutubeSabrFormat format : info.getFormats()) {
            final String mime = format.getMimeType();
            if (format.isAudio() && mime != null && mime.contains("mp4")
                    && (aac == null || format.getBitrate() > aac.getBitrate())) {
                aac = format;
            }
        }
        return aac != null ? aac : info.findBestAudioFormat();
    }

    @Nullable
    private static YoutubeSabrFormat pickVideoFormat(@NonNull final YoutubeSabrInfo info,
                                                     @Nullable final Integer selectedItag) {
        if (selectedItag != null && selectedItag > 0) {
            final YoutubeSabrFormat selected = info.findFormatByItag(selectedItag);
            if (selected != null && selected.isVideo()) {
                return selected;
            }
        }
        return info.findBestVideoFormat();
    }

    public static void evict(@NonNull final String videoId) {
        final Holder holder = SESSIONS.remove(videoId);
        ORDER.remove(videoId);
        if (holder != null && holder.pump != null) {
            holder.pump.stop();
        }
    }
}
