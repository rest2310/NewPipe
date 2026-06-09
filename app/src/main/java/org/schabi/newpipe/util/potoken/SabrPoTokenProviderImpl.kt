package org.schabi.newpipe.util.potoken

import android.util.Base64
import android.util.Log
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

object SabrPoTokenProviderImpl : SabrPoTokenProvider {
    private val tag = SabrPoTokenProviderImpl::class.simpleName

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState
    ): ByteArray? = getPoToken(info, streamState, false)

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState,
        forceRefresh: Boolean
    ): ByteArray? {
        val token = PoTokenProviderImpl.getWebClientPoToken(info.videoId, forceRefresh)
            ?.let { it.streamingDataPoToken ?: it.playerRequestPoToken }
            ?: return null

        return try {
            Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Could not decode SABR PO token", e)
            null
        }
    }
}
