package org.schabi.newpipe.player.datasource.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.MimeTypes;

import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;

/**
 * Tier-2 {@link com.google.android.exoplayer2.source.MediaSource} for SABR. Unlike the v1
 * ProgressiveMediaSource over a byte stream (which could not seek), this exposes a seekable
 * single-period timeline and a {@link SabrMediaPeriod} backed by the chunk framework, so seeking is
 * time-based and lands correctly. The session is created by the resolver and handed in.
 */
public final class SabrMediaSource extends BaseMediaSource {

    private final MediaItem mediaItem;
    private final SabrSessionStore.Holder holder;
    private final Localization localization;
    private final Format audioFormat;
    private final Format videoFormat;
    private final long durationUs;
    private final boolean exposeVideoTrack;
    private final boolean exposeAudioTrack;

    public SabrMediaSource(final MediaItem mediaItem,
                           final SabrSessionStore.Holder holder,
                           final Localization localization) {
        this(mediaItem, holder, localization, true, true);
    }

    public SabrMediaSource(final MediaItem mediaItem,
                           final SabrSessionStore.Holder holder,
                           final Localization localization,
                           final boolean exposeVideoTrack,
                           final boolean exposeAudioTrack) {
        this.mediaItem = mediaItem;
        this.holder = holder;
        this.localization = localization;
        this.audioFormat = toExoFormat(holder.audioFormat);
        this.videoFormat = toExoFormat(holder.videoFormat);
        this.durationUs = Math.max(holder.audioFormat.getApproxDurationMs(),
                holder.videoFormat.getApproxDurationMs()) * 1000L;
        this.exposeVideoTrack = exposeVideoTrack;
        this.exposeAudioTrack = exposeAudioTrack;
    }

    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        refreshSourceInfo(new SinglePeriodTimeline(durationUs, /* isSeekable= */ true,
                /* isDynamic= */ false, /* useLiveConfiguration= */ false,
                /* manifest= */ null, mediaItem));
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() {
    }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        return new SabrMediaPeriod(holder, audioFormat, videoFormat, durationUs, allocator,
                DrmSessionManager.DRM_UNSUPPORTED, createDrmEventDispatcher(id),
                createEventDispatcher(id), localization, exposeVideoTrack, exposeAudioTrack);
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        ((SabrMediaPeriod) mediaPeriod).release();
    }

    @Override
    protected void releaseSourceInternal() {
    }

    private static Format toExoFormat(final YoutubeSabrFormat f) {
        final String mime = f.getMimeType();
        String container = mime;
        String codecs = null;
        final int sc = mime.indexOf(';');
        if (sc > 0) {
            container = mime.substring(0, sc).trim();
        }
        final int ci = mime.indexOf("codecs=");
        if (ci >= 0) {
            codecs = mime.substring(ci + "codecs=".length()).replace("\"", "").trim();
        }
        final Format.Builder b = new Format.Builder()
                .setId(String.valueOf(f.getItag()))
                .setContainerMimeType(container)
                .setCodecs(codecs)
                .setSampleMimeType(codecs != null ? MimeTypes.getMediaMimeType(codecs) : container)
                .setAverageBitrate(f.getBitrate());
        if (f.isVideo()) {
            b.setWidth(f.getWidth()).setHeight(f.getHeight());
        }
        return b.build();
    }
}
