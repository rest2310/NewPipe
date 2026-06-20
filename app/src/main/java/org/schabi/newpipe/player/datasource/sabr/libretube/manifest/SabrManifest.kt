package org.schabi.newpipe.player.datasource.sabr.libretube.manifest

import android.net.Uri
import android.util.Base64
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.util.MimeTypes
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.Xtags

class SabrManifest(
    val videoId: String,
    val serverAbrStreamingUri: Uri,
    val videoPlaybackUstreamerConfig: ByteArray,
    val durationMs: Long
) {
    var adaptationSets: List<AdaptationSet> = emptyList()
        private set

    constructor(info: StreamInfo) : this(
        info.id,
        Uri.parse(requireNotNull(info.serverAbrStreamingUrl)),
        Base64.decode(requireNotNull(info.ustreamerConfig), Base64.URL_SAFE),
        info.duration * 1000
    ) {
        val videos = (info.videoStreams + info.videoOnlyStreams)
            .filter { it.deliveryMethod == DeliveryMethod.SABR }
        val audios = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.SABR }

        val videoSets = videos.groupBy { it.format?.mimeType.orEmpty() }.map { (_, streams) ->
            AdaptationSet(C.TRACK_TYPE_VIDEO, streams.map(::videoRepresentation))
        }
        val audioSets = audios.groupBy {
            it.format?.mimeType.orEmpty() + it.itagItem?.audioTrackId.orEmpty()
        }.map { (_, streams) ->
            AdaptationSet(C.TRACK_TYPE_AUDIO, streams.map(::audioRepresentation))
        }
        adaptationSets = videoSets + audioSets
    }

    companion object {
        private fun videoRepresentation(stream: VideoStream): Representation {
            val itag = requireNotNull(stream.itagItem)
            return Representation(
                Format.Builder()
                    .setId(stream.itag.toString())
                    .setCodecs(itag.codec)
                    .setContainerMimeType(stream.format?.mimeType)
                    .setSampleMimeType(MimeTypes.getVideoMediaMimeType(itag.codec))
                    .setAverageBitrate(stream.bitrate)
                    .setFrameRate(stream.fps.toFloat())
                    .setWidth(stream.width)
                    .setHeight(stream.height)
                    .build(),
                stream
            )
        }

        private fun audioRepresentation(stream: AudioStream): Representation {
            val itag = requireNotNull(stream.itagItem)
            val xtags = Xtags(itag.xtags.orEmpty())
            return Representation(
                Format.Builder()
                    .setId(stream.itag.toString())
                    .setCodecs(itag.codec)
                    .setContainerMimeType(stream.format?.mimeType)
                    .setSampleMimeType(MimeTypes.getAudioMediaMimeType(itag.codec))
                    .setAverageBitrate(stream.bitrate)
                    .setChannelCount(itag.audioChannels)
                    .setLanguage(itag.audioTrackId?.take(2) ?: xtags.language())
                    .build(),
                stream
            )
        }
    }
}
