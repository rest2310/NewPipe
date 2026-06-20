package org.schabi.newpipe.player.datasource.sabr.libretube.manifest

import com.google.android.exoplayer2.Format
import misc.Common.FormatId
import org.schabi.newpipe.extractor.stream.Stream

data class Representation(
    val format: Format,
    val stream: Stream
) {
    fun formatId(): FormatId {
        val itag = requireNotNull(stream.itagItem)
        return FormatId.newBuilder()
            .setItag(itag.id)
            .setLastModified(itag.lastModified)
            .setXtags(itag.xtags.orEmpty())
            .build()
    }
}
