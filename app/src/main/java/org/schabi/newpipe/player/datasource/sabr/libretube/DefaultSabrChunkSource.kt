package org.schabi.newpipe.player.datasource.sabr.libretube

import android.os.SystemClock
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.TrackType
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.extractor.ChunkIndex
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor
import com.google.android.exoplayer2.source.chunk.Chunk
import com.google.android.exoplayer2.source.chunk.ChunkExtractor
import com.google.android.exoplayer2.source.chunk.ChunkHolder
import com.google.android.exoplayer2.source.chunk.ChunkSource
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk
import com.google.android.exoplayer2.source.chunk.InitializationChunk
import com.google.android.exoplayer2.source.chunk.MediaChunk
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator
import com.google.android.exoplayer2.trackselection.ExoTrackSelection
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.FallbackOptions
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.google.android.exoplayer2.util.Util
import java.time.Instant
import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.Representation
import org.schabi.newpipe.player.datasource.sabr.libretube.manifest.SabrManifest
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.PlaybackRequest
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.SabrClient

/** A default SABR [ChunkSource] implementation. */
class DefaultSabrChunkSource(
    chunkExtractorFactory: ChunkExtractor.Factory,
    private val manifest: SabrManifest,
    private val sabrClient: SabrClient,
    private val adaptationSetIndices: IntArray,
    private var trackSelection: ExoTrackSelection,
    private val trackType: @TrackType Int,
    private val dataSource: DataSource,
    private val playerId: PlayerId
) : ChunkSource {

    private val representationHolders: MutableList<RepresentationHolder>

    private var fatalError: Exception? = null
    private var missingLastSegment = false

    /*
     * @param chunkExtractorFactory Creates [ChunkExtractor] instances to use for extracting
     * chunks.
     * @param manifest The initial manifest.
     * @param adaptationSetIndices The indices of the adaptation sets in the period.
     * @param trackSelection The track selection.
     * @param trackType The [type][C.TrackType] of the tracks in the selection.
     * @param dataSource A [DataSource] suitable for loading the media data.
     * @param playerId The [PlayerId] of the player using this chunk source.
     */
    init {
        val representations =
            adaptationSetIndices.flatMap { manifest.adaptationSets[it].representations }
                .filterNotNull().toList()
        representationHolders = (0..<trackSelection.length()).map {
            val representation = representations[trackSelection.getIndexInTrackGroup(it)]
            RepresentationHolder(
                Util.msToUs(
                    representation.stream.itagItem?.approxDurationMs
                        ?.takeIf { it > 0 } ?: manifest.durationMs
                ),
                representation,
                chunkExtractorFactory.createProgressiveMediaExtractor(
                    trackType,
                    representation.format,
                    false,
                    emptyList(),
                    null,
                    playerId
                )
            )
        }.toMutableList()
    }

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long {
        // inform the server when we last sought to a new position
        sabrClient.lastSeekMs = Instant.now().toEpochMilli()

        // Segments are aligned across representations, so any segment index will do.
        for (representationHolder in representationHolders) {
            if (representationHolder.chunkIndex != null) {
                val segmentCount = representationHolder.segmentCount
                if (segmentCount == 0L) {
                    continue
                }
                val segmentNum = representationHolder.getSegmentNum(positionUs)
                val firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum)
                val secondSyncUs = if (firstSyncUs < positionUs && (segmentNum < segmentCount - 1)) {
                    representationHolder.getSegmentStartTimeUs(segmentNum + 1)
                } else {
                    firstSyncUs
                }
                return seekParameters.resolveSeekPositionUs(positionUs, firstSyncUs, secondSyncUs)
            }
        }
        // We don't have a segment index to adjust the seek position with yet.
        return positionUs
    }

    fun updateTrackSelection(trackSelection: ExoTrackSelection?) {
        this.trackSelection = trackSelection!!
    }

    override fun maybeThrowError() {
        if (fatalError != null) {
            throw fatalError!!
        }
    }

    override fun getPreferredQueueSize(
        playbackPositionUs: Long,
        queue: MutableList<out MediaChunk>
    ): Int {
        if (fatalError != null || trackSelection.length() < 2) {
            return queue.size
        }
        return trackSelection.evaluateQueueSize(playbackPositionUs, queue)
    }

    override fun shouldCancelLoad(
        playbackPositionUs: Long,
        loadingChunk: Chunk,
        queue: MutableList<out MediaChunk>
    ): Boolean {
        if (fatalError != null) {
            return false
        }
        return trackSelection.shouldCancelChunkLoad(playbackPositionUs, loadingChunk, queue)
    }

    override fun getNextChunk(
        playbackPositionUs: Long,
        loadPositionUs: Long,
        queue: List<out MediaChunk>,
        out: ChunkHolder
    ) {
        if (fatalError != null) {
            return
        }

        val bufferedDurationUs = loadPositionUs - playbackPositionUs

        val previousChunk = queue.lastOrNull()

        val chunkIterators = representationHolders.map {
            if (it.chunkIndex == null) {
                MediaChunkIterator.EMPTY
            } else {
                val lastAvailableSegmentNum = it.getLastAvailableSegmentNum()

                val segmentNum = previousChunk?.nextChunkIndex ?: Util.constrainValue(
                    it.getSegmentNum(loadPositionUs),
                    0,
                    lastAvailableSegmentNum
                )

                RepresentationSegmentIterator(
                    it,
                    segmentNum,
                    lastAvailableSegmentNum
                )
            }
        }.toTypedArray()

        // adaptive track selection may change the selected for when called `updateSelectedTrack`.
        // this can lead to playback errors, if only one stream changes (e.g. video, but audio continues to be loaded).
        // We artificially delay this by only changing the format on the next selection,
        // ensuring there is some data already buffered (from the current data request).
        // FIXME: there is probably a better way
        val representationHolder = representationHolders[trackSelection.selectedIndex]
        sabrClient.selectFormat(representationHolder.representation)
        trackSelection.updateSelectedTrack(
            playbackPositionUs,
            bufferedDurationUs,
            C.TIME_UNSET,
            queue,
            chunkIterators
        )

        if (representationHolder.chunkExtractor != null) {
            if (representationHolder.chunkIndex == null) {
                // when we request a new format, it should start with an initialization chunk
                val dataSpec = DataSpec.Builder()
                    // must be non-null, but is unused
                    .setUri(manifest.serverAbrStreamingUri)
                    .setCustomData(
                        PlaybackRequest.initRequest(
                            representationHolder.representation.formatId(),
                            Util.usToMs(playbackPositionUs),
                            1f
                        )
                    )
                    .build()

                out.chunk = InitializationChunk(
                    dataSource,
                    dataSpec,
                    trackSelection.selectedFormat,
                    trackSelection.selectionReason,
                    trackSelection.selectionData,
                    representationHolder.chunkExtractor
                )
                return
            }
        }

        if (representationHolder.segmentCount == 0L) {
            // The index doesn't define any segments.
            out.endOfStream = true
            return
        }

        val lastAvailableSegmentNum = representationHolder.getLastAvailableSegmentNum()
        val segmentNum = previousChunk?.nextChunkIndex ?: Util.constrainValue(
            representationHolder.getSegmentNum(loadPositionUs),
            0,
            lastAvailableSegmentNum
        )

        if (segmentNum > lastAvailableSegmentNum ||
            (missingLastSegment && segmentNum >= lastAvailableSegmentNum)
        ) {
            // The segment is beyond the end of the period.
            out.endOfStream = true
            return
        }

        if (representationHolder.getSegmentStartTimeUs(segmentNum) >= representationHolder.periodDurationUs) {
            // The period duration clips the period to a position before the segment.
            out.endOfStream = true
            return
        }

        val seekTimeUs = if (queue.isEmpty()) loadPositionUs else C.TIME_UNSET
        val startTimeUs = representationHolder.getSegmentStartTimeUs(segmentNum)

        // use the queue to build the buffered segments
        // each queue media chunk corresponds to 1 segment
        val bufferedSegments = queue.mapNotNull { (it.dataSpec.customData as PlaybackRequest?)?.segment }
        val dataSpec = DataSpec.Builder()
            // must be non-null, but is unused
            .setUri(manifest.serverAbrStreamingUri)
            .setCustomData(
                PlaybackRequest(
                    representationHolder.representation.formatId(),
                    Util.usToMs(playbackPositionUs),
                    1f,
                    // the chunk index doesn't count the index segment as segment 0
                    segmentNum + 1,
                    Util.usToMs(startTimeUs),
                    bufferedSegments
                )
            )
            .build()

        out.chunk = ContainerMediaChunk(
            dataSource,
            dataSpec,
            trackSelection.selectedFormat,
            trackSelection.selectionReason,
            trackSelection.selectionData,
            startTimeUs,
            representationHolder.getSegmentEndTimeUs(segmentNum),
            seekTimeUs,
            representationHolder.periodDurationUs,
            segmentNum,
            1,
            0,
            representationHolder.chunkExtractor!!
        )
    }

    override fun onChunkLoadCompleted(chunk: Chunk) {
        if (chunk is InitializationChunk) {
            val trackIndex = trackSelection.indexOf(chunk.trackFormat)
            val representationHolder = representationHolders[trackIndex]
            // The null check avoids overwriting an index obtained from the manifest with one obtained
            // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
            // where it does we should ignore it.
            if (representationHolder.chunkIndex == null) {
                representationHolder.chunkExtractor?.chunkIndex?.let {
                    representationHolders[trackIndex].chunkIndex = it
                }
            }
        }
    }

    override fun onChunkLoadError(
        chunk: Chunk,
        cancelable: Boolean,
        loadErrorInfo: LoadErrorInfo,
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): Boolean {
        if (!cancelable) {
            return false
        }
        // Workaround for missing segment at the end of the period
        if (chunk is MediaChunk &&
            loadErrorInfo.exception is InvalidResponseCodeException &&
            (loadErrorInfo.exception as InvalidResponseCodeException).responseCode == 404
        ) {
            val representationHolder =
                representationHolders[trackSelection.indexOf(chunk.trackFormat)]
            val segmentCount = representationHolder.segmentCount
            if (segmentCount != 0L) {
                val lastAvailableSegmentNum = segmentCount - 1
                if (chunk.nextChunkIndex > lastAvailableSegmentNum) {
                    missingLastSegment = true
                    return true
                }
            }
        }

        val fallbackOptions = createFallbackOptions(trackSelection)
        if (!fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) &&
            !fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION)
        ) {
            return false
        }
        val fallbackSelection =
            loadErrorHandlingPolicy.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
        if (fallbackSelection == null || !fallbackOptions.isFallbackAvailable(fallbackSelection.type)) {
            // Policy indicated to not use any fallback or a fallback type that is not available.
            return false
        }

        var cancelLoad = false
        if (fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
            cancelLoad =
                trackSelection.excludeTrack(
                    trackSelection.indexOf(chunk.trackFormat),
                    fallbackSelection.exclusionDurationMs
                )
        }
        return cancelLoad
    }

    override fun release() {
        for (representationHolder in representationHolders) {
            representationHolder.chunkExtractor?.release()
        }
    }

    private fun createFallbackOptions(trackSelection: ExoTrackSelection): FallbackOptions {
        val nowMs = SystemClock.elapsedRealtime()
        val numberOfTracks = trackSelection.length()
        var numberOfExcludedTracks = 0
        for (i in 0..<numberOfTracks) {
            if (trackSelection.isTrackExcluded(i, nowMs)) {
                numberOfExcludedTracks++
            }
        }
        return FallbackOptions(
            0,
            0,
            numberOfTracks,
            numberOfExcludedTracks
        )
    }

    /** [MediaChunkIterator] wrapping a [RepresentationHolder]. */
    class RepresentationSegmentIterator(
        private val representationHolder: RepresentationHolder,
        firstAvailableSegmentNum: Long,
        lastAvailableSegmentNum: Long
    ) : BaseMediaChunkIterator(firstAvailableSegmentNum, lastAvailableSegmentNum) {
        override fun getDataSpec(): DataSpec {
            checkInBounds()
            val dataSpec = DataSpec.Builder()
                // must be non-null, but is unused
                .setUri("sabr://unused")
                .build()
            return dataSpec
        }

        override fun getChunkStartTimeUs(): Long {
            checkInBounds()
            return representationHolder.getSegmentStartTimeUs(currentIndex)
        }

        override fun getChunkEndTimeUs(): Long {
            checkInBounds()
            return representationHolder.getSegmentEndTimeUs(currentIndex)
        }
    }

    /** Holds information about a snapshot of a single [Representation].  */
    data class RepresentationHolder(
        val periodDurationUs: Long,
        val representation: Representation,
        val chunkExtractor: ChunkExtractor?
    ) {
        var chunkIndex: ChunkIndex? = null

        val segmentCount: Long
            get() = chunkIndex?.length?.toLong() ?: 0

        fun getSegmentStartTimeUs(segmentNum: Long): Long = chunkIndex!!.timesUs[segmentNum.toInt()]

        fun getSegmentEndTimeUs(segmentNum: Long): Long = (getSegmentStartTimeUs(segmentNum) + chunkIndex!!.durationsUs[segmentNum.toInt()])

        fun getSegmentNum(positionUs: Long): Long = chunkIndex!!.getChunkIndex(positionUs).toLong()

        fun getLastAvailableSegmentNum(): Long = chunkIndex!!.length.toLong() - 1
    }
}
