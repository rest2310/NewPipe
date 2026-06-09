package org.schabi.newpipe.player.datasource.sabr;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractor;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ChunkSource;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;

import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat;

import java.io.IOException;
import java.util.List;

/**
 * Tier-2: feeds the media3 chunk framework one SABR segment per chunk. Because the framework drives
 * loading by chunk INDEX (mapped from time), seeking is time-based and real, unlike the v1 byte
 * stream that could not land a seek. One {@link FragmentedMp4Extractor} is shared per track via a
 * {@link BundledChunkExtractor}; the init segment is loaded once as an {@link InitializationChunk},
 * then each media segment is a {@link ContainerMediaChunk}.
 */
final class SabrChunkSource implements ChunkSource {

    private final SabrSessionStore.Holder holder;
    private final YoutubeSabrFormat format;
    private final Format trackFormat;
    private final int trackType;
    private final Localization localization;

    @Nullable
    private IOException fatalError;

    SabrChunkSource(final SabrSessionStore.Holder holder,
                    final YoutubeSabrFormat format,
                    final Format trackFormat,
                    final int trackType,
                    final Localization localization) {
        this.holder = holder;
        this.format = format;
        this.trackFormat = trackFormat;
        this.trackType = trackType;
        this.localization = localization;
    }

    @Override
    public long getAdjustedSeekPositionUs(final long positionUs,
                                          final SeekParameters seekParameters) {
        // Snap to the start of the segment that contains positionUs.
        final int seq = holder.session.getStreamState()
                .getSegmentNumberAtOrAfterTimeMs(format, positionUs / 1000);
        final long startMs = holder.session.getStreamState().getSegmentStartMs(format, seq);
        return Math.max(0, startMs) * 1000;
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (fatalError != null) {
            throw fatalError;
        }
    }

    @Override
    public int getPreferredQueueSize(final long playbackPositionUs,
                                     final List<? extends MediaChunk> queue) {
        return queue.size();
    }

    @Override
    public boolean shouldCancelLoad(final long playbackPositionUs, final Chunk loadingChunk,
                                    final List<? extends MediaChunk> queue) {
        return false;
    }

    @Override
    public void getNextChunk(final long playbackPositionUs, final long loadPositionUs,
                             final List<? extends MediaChunk> queue, final ChunkHolder out) {
        final int nextSeq;
        if (queue.isEmpty()) {
            nextSeq = holder.session.getStreamState()
                    .getSegmentNumberAtOrAfterTimeMs(format, loadPositionUs / 1000);
        } else {
            nextSeq = (int) (queue.get(queue.size() - 1).getNextChunkIndex());
        }
        final long endSeq = holder.session.getStreamState().getEndSegment(format);
        if (endSeq > 0 && nextSeq > endSeq) {
            out.endOfStream = true;
            return;
        }
        out.chunk = newMediaChunk(nextSeq);
    }

    private Chunk newMediaChunk(final int seq) {
        final long startMs = holder.session.getStreamState().getSegmentStartMs(format, seq);
        final long endMs = holder.session.getStreamState().getSegmentEndMs(format, seq);
        final long startUs = Math.max(0, startMs) * 1000;
        final long endUs = (endMs > 0 ? endMs : startMs) * 1000;
        final DataSpec spec = new DataSpec(Uri.parse("sabrseg://" + format.getItag() + "/" + seq));
        // Fresh extractor per chunk: the data source prepends the init, so each chunk is a complete
        // init + one fragment. Absolute fragment timestamps -> sampleOffsetUs = 0.
        // YouTube ships VP9/Opus in WebM and AVC/AAC in fragmented mp4.
        final String mime = format.getMimeType();
        final Extractor extractorImpl = mime != null && mime.contains("webm")
                ? new MatroskaExtractor()
                : new FragmentedMp4Extractor();
        final ChunkExtractor extractor = new BundledChunkExtractor(
                extractorImpl, trackType, trackFormat);
        return new ContainerMediaChunk(
                new SabrSegmentDataSource(holder, format, localization, /* prependInit= */ true),
                spec, trackFormat, C.SELECTION_REASON_UNKNOWN, null,
                startUs, endUs, /* clippedStartTimeUs= */ startUs, /* clippedEndTimeUs= */ endUs,
                /* chunkIndex= */ seq, /* chunkCount= */ 1, /* sampleOffsetUs= */ 0L,
                extractor);
    }

    @Override
    public void onChunkLoadCompleted(final Chunk chunk) {
    }

    @Override
    public boolean onChunkLoadError(final Chunk chunk, final boolean cancelable,
                                    final LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo,
                                    final LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        // Let the framework apply its retry/backoff policy.
        return false;
    }

    @Override
    public void release() {
    }
}
