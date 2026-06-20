package org.schabi.newpipe.player.datasource.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.SabrManifest;
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.SabrClient;

/** A seekable, single-period media source backed by LibreTube's SABR client. */
public final class SabrMediaSource extends BaseMediaSource {
    private final MediaItem mediaItem;
    private final SabrManifest manifest;
    private final SabrClient client;
    private final boolean exposeVideoTrack;
    private final boolean exposeAudioTrack;

    public SabrMediaSource(final MediaItem mediaItem, final SabrManifest manifest) {
        this(mediaItem, manifest, true, true);
    }

    public SabrMediaSource(final MediaItem mediaItem, final SabrManifest manifest,
                           final boolean exposeVideoTrack, final boolean exposeAudioTrack) {
        this.mediaItem = mediaItem;
        this.manifest = manifest;
        this.client = new SabrClient(manifest);
        this.exposeVideoTrack = exposeVideoTrack;
        this.exposeAudioTrack = exposeAudioTrack;
    }

    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        refreshSourceInfo(new SinglePeriodTimeline(manifest.getDurationMs() * 1000L,
                true, false, false, manifest, mediaItem));
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() {
    }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        return new SabrMediaPeriod(manifest, client, allocator,
                DrmSessionManager.DRM_UNSUPPORTED, createDrmEventDispatcher(id),
                createEventDispatcher(id), (exposeVideoTrack ? 1 << C.TRACK_TYPE_VIDEO : 0)
                        | (exposeAudioTrack ? 1 << C.TRACK_TYPE_AUDIO : 0));
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        ((SabrMediaPeriod) mediaPeriod).release();
    }

    @Override
    protected void releaseSourceInternal() {
    }
}
