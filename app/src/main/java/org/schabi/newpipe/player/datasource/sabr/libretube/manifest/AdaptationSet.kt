package org.schabi.newpipe.player.datasource.sabr.libretube.manifest

data class AdaptationSet(
    val type: Int,
    val representations: List<Representation>
)
