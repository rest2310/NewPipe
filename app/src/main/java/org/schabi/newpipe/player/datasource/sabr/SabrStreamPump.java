package org.schabi.newpipe.player.datasource.sabr;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;

import java.io.IOException;
import java.util.List;

/**
 * Single consumer of a {@link YoutubeSabrSession}: one daemon thread pumps the server-driven SABR
 * stream and fills the session's (concurrent) segment cache ahead of the play head. The server
 * paces us with policy-only responses once we are far enough ahead. Both the audio and video
 * {@link SabrSegmentDataSource}s only read the cache, so they never fight over the session or
 * block each other on a network round-trip.
 */
final class SabrStreamPump {

    private static final String TAG = "SabrStreamPump";
    private static final long IDLE_POLL_MS = 400;     // server paced us / nothing new this round
    private static final long ERROR_RETRY_MS = 1000;  // transient network error
    // no reads for this long -> playback is gone. MUST stay above READAHEAD_CUSHION_MS: once the
    // player buffer is full it stops reading us for ~cushion seconds, and killing the pump in that
    // window left the cache to drain dry -> periodic rebuffering.
    private static final long IDLE_STOP_MS = 90_000;
    // Margin the buffered edge stays ahead of the furthest-read track. Driven off the reader
    // it only needs to cover a few segments, so it stays small.
    private static final long READAHEAD_CUSHION_MS = 30_000;
    // Hard byte ceiling on read-ahead so high-bitrate streams do not exhaust the heap.
    // ~100MB still covers the player's ~30s read-ahead.
    private static final long MAX_AHEAD_BYTES = 100L * 1024 * 1024;
    // Keep this much already-played video in the cache so a short backward seek lands on cached
    // segments instead of a hole (eviction used to drop everything the reader passed, so any rewind
    // hit an evicted segment the pump never re-fetches -> dead buffer). Bounded, same order as the
    // forward cushion. Rewinds beyond this still need a session re-request (separate follow-up).
    private static final long BACK_BUFFER_MS = 30_000;
    // Fallback back-buffer used when the cache is already over the byte budget.
    // a 30s back-buffer + readahead exceeds MAX_AHEAD_BYTES, and since eviction can't drop segments
    // within the back-buffer window the cache can't drain -> the pump throttles forever and stalls.
    // Shrinking the back-buffer lets eviction free bytes so playback keeps fetching.
    private static final long MIN_BACK_BUFFER_MS = 5_000;
    private static final long SEEK_AHEAD_THRESHOLD_MS = 1_000;

    private final YoutubeSabrSession session;
    private final SabrSessionStore.Holder holder;
    private final Localization localization;

    private volatile boolean started;
    private volatile boolean stopped;
    private volatile boolean fatal;
    private volatile long lastReadMs;
    // Set by a reader blocked on a missing segment. Behind the edge this is a rewind/refetch;
    // ahead of the edge it is active demand and bypasses normal read-ahead throttling.
    private volatile SabrSegmentRequest pendingRefetch;
    private Thread thread;

    SabrStreamPump(@NonNull final YoutubeSabrSession session,
                   @NonNull final SabrSessionStore.Holder holder,
                   @NonNull final Localization localization) {
        this.session = session;
        this.holder = holder;
        this.localization = localization;
    }

    /** Start or restart the pump thread, and mark the session as actively read. */
    void ensureStarted() {
        lastReadMs = System.currentTimeMillis();
        if (fatal || (started && !stopped)) {
            return;
        }
        synchronized (this) {
            if (fatal || (started && !stopped)) {
                return;
            }
            stopped = false;
            started = true;
            thread = new Thread(this::loop, "SabrStreamPump");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /** Stop the pump thread and release it (called on eviction / playback teardown). */
    void stop() {
        synchronized (this) {
            stopped = true;
            // Don't self-interrupt: stop() is also reached from the pump thread itself via
            // evict-on-fatal, and setting our own interrupt flag could break a later blocking call.
            if (thread != null && thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
    }

    @Nullable
    SabrMediaSegment getCached(@NonNull final SabrSegmentRequest request) {
        // revive the pump if it idled out: any read means playback is live again.
        ensureStarted();
        return session.getCachedSegment(request);
    }

    boolean isFatal() {
        return fatal;
    }

    /**
     * Ask the loop to reposition the session onto an evicted segment.
     *
     * @param request the segment to refetch
     */
    void requestRefetchFrom(@NonNull final SabrSegmentRequest request) {
        pendingRefetch = request;
        ensureStarted();
    }

    private void loop() {
        try {
            while (!stopped) {
                if (System.currentTimeMillis() - lastReadMs > IDLE_STOP_MS
                        || session.isComplete()) {
                    break;
                }
                try {
                    // Drive off what the player has actually read, not the play head: the play head
                    // freezes while buffering. readerHead = furthest
                    // track read; readerTail = slowest track read (safe to evict below).
                    final long readerHeadMs = holder.getReaderHeadMs();
                    // Evict what both tracks have read past every round, keeping BACK_BUFFER_MS
                    // behind the reader so a short backward seek finds cached segments.
                    // When over the byte budget, shrink the back-buffer so eviction can drain it.
                    final long backBufferMs = session.getCachedBytes() > MAX_AHEAD_BYTES
                            ? MIN_BACK_BUFFER_MS : BACK_BUFFER_MS;
                    session.setPlayHeadMs(Math.max(0, holder.getReaderTailMs() - backBufferMs));
                    session.evictPlayed();
                    final long edgeMs = session.getStreamState().getMinBufferedEndMs();
                    // A reader is blocked on a concrete segment. Reposition the session and ask
                    // immediately, bypassing read-ahead/byte throttles by design.
                    final SabrSegmentRequest refetch = pendingRefetch;
                    if (refetch != null) {
                        pendingRefetch = null;
                        final long refetchStartMs = session.getStreamState()
                                .getSegmentStartMs(refetch.getFormat(),
                                        refetch.getSequenceNumber());
                        if (refetchStartMs < edgeMs) {
                            session.prepareForRewind(refetch);
                        } else {
                            session.prepareForMediaSegment(refetch);
                        }
                        session.pumpOnce(localization);
                        continue;
                    }
                    if (readerHeadMs > edgeMs + SEEK_AHEAD_THRESHOLD_MS) {
                        final int seq = session.getStreamState()
                                .getSegmentNumberAtOrAfterTimeMs(holder.videoFormat, readerHeadMs);
                        session.prepareForMediaSegment(SabrSegmentRequest.media(
                                holder.videoFormat, seq));
                        session.pumpOnce(localization);
                        continue;
                    }
                    final boolean throttled = edgeMs - readerHeadMs > READAHEAD_CUSHION_MS
                            || session.getCachedBytes() > MAX_AHEAD_BYTES;
                    if (throttled) {
                        Thread.sleep(IDLE_POLL_MS);
                        continue;
                    }
                    // Report the contiguous buffered edge: the server fills from the
                    // reported position, so reporting readerHead (ahead of a laggard track) made it
                    // skip past the gap and the slow track's edge never advanced.
                    // Pace on readerHead,
                    // report on edge.
                    session.getStreamState().setPlayerTimeMs(edgeMs);
                    final List<SabrMediaSegment> segments = session.pumpOnce(localization);
                    if (!segments.isEmpty()) {
                        holder.prewarmPoToken();
                    }
                    if (segments.isEmpty()) {
                        Thread.sleep(IDLE_POLL_MS);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final IOException e) {
                    sleepQuietly(ERROR_RETRY_MS);
                } catch (final ExtractionException e) {
                    Log.i(TAG, "SABR pump fatal: " + e.getMessage());
                    fatal = true;
                    // Drop the dead session so a re-open rebuilds a fresh one.
                    SabrSessionStore.evict(holder.videoId);
                    break;
                }
            }
        } finally {
            synchronized (this) {
                stopped = true;
            }
        }
    }

    private static void sleepQuietly(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
