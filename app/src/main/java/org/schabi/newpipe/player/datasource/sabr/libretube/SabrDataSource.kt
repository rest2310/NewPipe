package org.schabi.newpipe.player.datasource.sabr.libretube

import android.net.Uri
import androidx.core.net.toUri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import java.io.IOException
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.CompositeBuffer
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.PlaybackRequest
import org.schabi.newpipe.player.datasource.sabr.libretube.parser.SabrClient

class SabrDataSource(
    private val sabrClient: SabrClient
) : BaseDataSource(true) {
    private var data: CompositeBuffer? = null

    class Factory(
        private val sabrClient: SabrClient
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SabrDataSource(sabrClient)
    }

    override fun open(dataSpec: DataSpec): Long {
        val playbackRequest = dataSpec.customData as PlaybackRequest?

        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        val segment = runCatching { sabrClient.getNextSegment(playbackRequest!!) }
            .getOrNull() ?: throw IOException()
        data = CompositeBuffer(segment.data)
        return data!!.remaining().toLong()
    }

    override fun getUri(): Uri? {
        if (data?.hasRemaining() != true) {
            // signal that this data source failed to be opened
            return null
        }
        return sabrClient.url.toUri()
    }

    override fun close() {
        transferEnded()
        data = null
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        assert(data != null)
        if (length == 0) {
            return 0
        }

        if (!data!!.hasRemaining()) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = minOf(length, data!!.remaining())
        data!!.read(buffer, offset, bytesToRead)

        // this is not the actual amount of bytes transferred, since the SABR stream has some overhead,
        // e.g. for format metadata
        bytesTransferred(bytesToRead)
        return bytesToRead
    }
}
