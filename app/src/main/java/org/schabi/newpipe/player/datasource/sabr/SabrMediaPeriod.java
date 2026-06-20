package org.schabi.newpipe.player.datasource.sabr;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import org.schabi.newpipe.player.datasource.sabr.libretube.DefaultSabrChunkSource;
import org.schabi.newpipe.player.datasource.sabr.libretube.SabrDataSource;
import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.AdaptationSet;
import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.Representation;
import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.SabrManifest;
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.SabrClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** ExoPlayer 2.19 adapter for LibreTube's demand-driven SABR chunk pipeline. */
final class SabrMediaPeriod implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<DefaultSabrChunkSource>> {
    private final SabrManifest manifest;
    private final SabrClient client;
    private final Allocator allocator;
    private final DrmSessionManager drmSessionManager;
    private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
    private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy =
            new DefaultLoadErrorHandlingPolicy();
    private final TrackGroupArray trackGroups;
    private final int[] adaptationSetIndices;
    private final int[] trackTypes;
    private final List<ChunkSampleStream<DefaultSabrChunkSource>> streams = new ArrayList<>();

    private SequenceableLoader compositeLoader = new EmptyLoader();
    @Nullable
    private MediaPeriod.Callback callback;

    SabrMediaPeriod(final SabrManifest manifest,
                    final SabrClient client,
                    final Allocator allocator,
                    final DrmSessionManager drmSessionManager,
                    final DrmSessionEventListener.EventDispatcher drmEventDispatcher,
                    final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
                    final int exposedTrackTypes) {
        this.manifest = manifest;
        this.client = client;
        this.allocator = allocator;
        this.drmSessionManager = drmSessionManager;
        this.drmEventDispatcher = drmEventDispatcher;
        this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;

        final List<AdaptationSet> adaptationSets = manifest.getAdaptationSets();
        final List<Integer> includedIndices = new ArrayList<>();
        for (int i = 0; i < adaptationSets.size(); i++) {
            final int type = adaptationSets.get(i).getType();
            if ((exposedTrackTypes & (1 << type)) != 0) {
                includedIndices.add(i);
            }
        }
        final TrackGroup[] groups = new TrackGroup[includedIndices.size()];
        adaptationSetIndices = new int[includedIndices.size()];
        trackTypes = new int[includedIndices.size()];
        for (int i = 0; i < includedIndices.size(); i++) {
            final int adaptationSetIndex = includedIndices.get(i);
            final AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);
            final List<Representation> representations = adaptationSet.getRepresentations();
            final Format[] formats = new Format[representations.size()];
            for (int j = 0; j < representations.size(); j++) {
                formats[j] = representations.get(j).getFormat();
            }
            groups[i] = new TrackGroup("sabr-" + i, formats);
            adaptationSetIndices[i] = adaptationSetIndex;
            trackTypes[i] = adaptationSet.getType();
        }
        trackGroups = new TrackGroupArray(groups);
    }

    @Override
    public void prepare(final MediaPeriod.Callback cb, final long positionUs) {
        callback = cb;
        cb.onPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() {
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return trackGroups;
    }

    @Override
    public long selectTracks(final ExoTrackSelection[] selections, final boolean[] mayRetainFlags,
                             final SampleStream[] outStreams, final boolean[] streamResetFlags,
                             final long positionUs) {
        final long now = Instant.now().toEpochMilli();
        client.setLastManualFormatSelectionMs(now);
        client.setLastActionMs(now);

        for (int i = 0; i < selections.length; i++) {
            if (outStreams[i] instanceof ChunkSampleStream && (selections[i] == null
                    || !mayRetainFlags[i])) {
                @SuppressWarnings("unchecked")
                final ChunkSampleStream<DefaultSabrChunkSource> stream =
                        (ChunkSampleStream<DefaultSabrChunkSource>) outStreams[i];
                streams.remove(stream);
                stream.release();
                outStreams[i] = null;
            }
            if (outStreams[i] == null && selections[i] != null) {
                final ChunkSampleStream<DefaultSabrChunkSource> stream =
                        buildStream(selections[i], positionUs);
                streams.add(stream);
                outStreams[i] = stream;
                streamResetFlags[i] = true;
            } else if (outStreams[i] instanceof ChunkSampleStream && selections[i] != null) {
                @SuppressWarnings("unchecked")
                final ChunkSampleStream<DefaultSabrChunkSource> stream =
                        (ChunkSampleStream<DefaultSabrChunkSource>) outStreams[i];
                stream.getChunkSource().updateTrackSelection(selections[i]);
            }
        }
        rebuildCompositeLoader();
        return positionUs;
    }

    private ChunkSampleStream<DefaultSabrChunkSource> buildStream(
            final ExoTrackSelection selection, final long positionUs) {
        final int groupIndex = trackGroups.indexOf(selection.getTrackGroup());
        final DefaultSabrChunkSource chunkSource = new DefaultSabrChunkSource(
                BundledChunkExtractor.FACTORY, manifest, client,
                new int[]{adaptationSetIndices[groupIndex]}, selection, trackTypes[groupIndex],
                new SabrDataSource(client), PlayerId.UNSET);
        return new ChunkSampleStream<>(trackTypes[groupIndex], null, null, chunkSource, this,
                allocator, positionUs, drmSessionManager, drmEventDispatcher,
                loadErrorHandlingPolicy, mediaSourceEventDispatcher);
    }

    private void rebuildCompositeLoader() {
        compositeLoader = new SequenceableLoader() {
            @Override
            public long getBufferedPositionUs() {
                long minimum = Long.MAX_VALUE;
                for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
                    final long position = stream.getBufferedPositionUs();
                    if (position != C.TIME_END_OF_SOURCE) {
                        minimum = Math.min(minimum, position);
                    }
                }
                return minimum == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : minimum;
            }

            @Override
            public long getNextLoadPositionUs() {
                long minimum = Long.MAX_VALUE;
                for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
                    final long position = stream.getNextLoadPositionUs();
                    if (position != C.TIME_END_OF_SOURCE) {
                        minimum = Math.min(minimum, position);
                    }
                }
                return minimum == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : minimum;
            }

            @Override
            public boolean continueLoading(final long positionUs) {
                boolean continued = false;
                for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
                    continued |= stream.continueLoading(positionUs);
                }
                return continued;
            }

            @Override
            public boolean isLoading() {
                for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
                    if (stream.isLoading()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void reevaluateBuffer(final long positionUs) {
                for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
                    stream.reevaluateBuffer(positionUs);
                }
            }
        };
    }

    @Override
    public void discardBuffer(final long positionUs, final boolean toKeyframe) {
        for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
            stream.discardBuffer(positionUs, toKeyframe);
        }
    }

    @Override
    public long readDiscontinuity() {
        return C.TIME_UNSET;
    }

    @Override
    public long seekToUs(final long positionUs) {
        for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
            stream.seekToUs(positionUs);
        }
        return positionUs;
    }

    @Override
    public long getAdjustedSeekPositionUs(final long positionUs,
                                          final SeekParameters seekParameters) {
        for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
            if (stream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
                return stream.getAdjustedSeekPositionUs(positionUs, seekParameters);
            }
        }
        return positionUs;
    }

    @Override
    public long getBufferedPositionUs() {
        return compositeLoader.getBufferedPositionUs();
    }

    @Override
    public long getNextLoadPositionUs() {
        return compositeLoader.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(final long positionUs) {
        return compositeLoader.continueLoading(positionUs);
    }

    @Override
    public boolean isLoading() {
        return compositeLoader.isLoading();
    }

    @Override
    public void reevaluateBuffer(final long positionUs) {
        compositeLoader.reevaluateBuffer(positionUs);
    }

    @Override
    public void onContinueLoadingRequested(
            final ChunkSampleStream<DefaultSabrChunkSource> source) {
        if (callback != null) {
            callback.onContinueLoadingRequested(this);
        }
    }

    void release() {
        for (final ChunkSampleStream<DefaultSabrChunkSource> stream : streams) {
            stream.release();
        }
        streams.clear();
    }

    private static final class EmptyLoader implements SequenceableLoader {
        @Override
        public long getBufferedPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public long getNextLoadPositionUs() {
            return C.TIME_END_OF_SOURCE;
        }

        @Override
        public boolean continueLoading(final long positionUs) {
            return false;
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public void reevaluateBuffer(final long positionUs) {
        }
    }
}
