package org.schabi.newpipe.util.potoken

import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

object SabrPoTokenProviderImpl {
    fun getPoTokenResult(videoId: String, forceRefresh: Boolean): PoTokenResult? = PoTokenProviderImpl.getWebClientPoToken(videoId, forceRefresh)
}
