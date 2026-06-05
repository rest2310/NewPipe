package org.schabi.newpipe.local.subscription.item

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.image.CoilHelper

class ChannelItem(
    val infoItem: ChannelInfoItem,
    private val subscriptionId: Long = -1L,
    val isFavorite: Boolean = false,
    private val isExpanded: Boolean = false,
    private val isLoadingLatestVideos: Boolean = false,
    private val latestVideos: List<StreamInfoItem> = emptyList(),
    var itemVersion: ItemVersion = ItemVersion.NORMAL,
    var gesturesListener: OnClickGesture<ChannelInfoItem>? = null,
    var favoriteClickListener: ((ChannelInfoItem, Boolean) -> Unit)? = null,
    var unsubscribeClickListener: ((ChannelInfoItem) -> Unit)? = null,
    var expandClickListener: ((ChannelInfoItem) -> Unit)? = null,
    var latestVideoClickListener: ((StreamInfoItem) -> Unit)? = null
) : Item<GroupieViewHolder>() {
    override fun getId(): Long = if (subscriptionId == -1L) super.getId() else subscriptionId

    enum class ItemVersion { NORMAL, MINI, GRID }

    override fun getLayout(): Int = when (itemVersion) {
        ItemVersion.NORMAL -> R.layout.list_channel_item
        ItemVersion.MINI -> R.layout.list_channel_mini_item
        ItemVersion.GRID -> R.layout.list_channel_grid_item
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        val itemTitleView = viewHolder.root.findViewById<TextView>(R.id.itemTitleView)
        val itemAdditionalDetails = viewHolder.root.findViewById<TextView>(R.id.itemAdditionalDetails)
        val itemChannelDescriptionView = viewHolder.root.findViewById<View?>(R.id.itemChannelDescriptionView) as? TextView
        val itemThumbnailView = viewHolder.root.findViewById<ImageView>(R.id.itemThumbnailView)
        val favoriteButton = viewHolder.root.findViewById<View?>(R.id.favoriteButton) as? ImageButton
        val subscribedButton = viewHolder.root.findViewById<View?>(R.id.subscribedButton) as? TextView
        val expandButton = viewHolder.root.findViewById<View?>(R.id.expandButton) as? ImageButton
        val channelInfoClickArea = viewHolder.root.findViewById<View?>(R.id.channelInfoClickArea)
        val latestVideosContainer = viewHolder.root.findViewById<View?>(R.id.latestVideosContainer) as? LinearLayout

        itemTitleView.text = infoItem.name
        itemAdditionalDetails.text = getDetailLine(viewHolder.root.context)
        if (itemVersion == ItemVersion.NORMAL) {
            itemChannelDescriptionView?.text = infoItem.description
        }

        CoilHelper.loadAvatar(itemThumbnailView, infoItem.thumbnails)
        bindSubscriptionActions(favoriteButton, subscribedButton, expandButton)
        bindLatestVideos(latestVideosContainer)

        gesturesListener?.run {
            val openChannel = View.OnClickListener { selected(infoItem) }
            channelInfoClickArea?.setOnClickListener(openChannel)
            itemThumbnailView.setOnClickListener(openChannel)
            itemTitleView.setOnClickListener(openChannel)
            itemAdditionalDetails.setOnClickListener(openChannel)
            channelInfoClickArea?.setOnLongClickListener {
                held(infoItem)
                true
            }
            viewHolder.root.setOnClickListener(null)
            viewHolder.root.setOnLongClickListener(null)
        }
    }

    private fun bindSubscriptionActions(
        favoriteButton: ImageButton?,
        subscribedButton: TextView?,
        expandButton: ImageButton?
    ) {
        favoriteButton?.apply {
            alpha = 1.0f
            setImageResource(if (isFavorite) R.drawable.ic_favorite_active else R.drawable.ic_favorite_inactive)
            setOnClickListener {
                favoriteClickListener?.invoke(infoItem, !isFavorite)
            }
        }

        subscribedButton?.setOnClickListener {
            unsubscribeClickListener?.invoke(infoItem)
        }

        expandButton?.apply {
            if (isLoadingLatestVideos) {
                rotation = 0.0f
                setImageResource(R.drawable.avd_subscription_spinner)
                (drawable as? Animatable)?.start()
            } else {
                setImageResource(R.drawable.ic_expand_more)
                rotation = if (isExpanded) 180.0f else 0.0f
            }
            setOnClickListener {
                expandClickListener?.invoke(infoItem)
            }
        }
    }

    private fun bindLatestVideos(latestVideosContainer: LinearLayout?) {
        latestVideosContainer ?: return

        val wasExpanded = latestVideosContainer.getTag(R.id.latestVideosContainer) as? Boolean ?: false
        latestVideosContainer.setTag(R.id.latestVideosContainer, isExpanded)

        if (isLoadingLatestVideos) {
            return
        }

        if (!isExpanded) {
            collapseLatestVideos(latestVideosContainer, wasExpanded)
            return
        }

        val animateIn = !wasExpanded
        populateLatestVideos(latestVideosContainer, animateIn)
        latestVideosContainer.visibility = View.VISIBLE
        if (!animateIn) {
            latestVideosContainer.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            return
        }

        latestVideosContainer.layoutParams.height = 0
        latestVideosContainer.post {
            val targetHeight = measureExpandedHeight(latestVideosContainer)
            val heightDuration = 300L
            animateHeight(latestVideosContainer, 0, targetHeight, heightDuration) {
                latestVideosContainer.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                latestVideosContainer.requestLayout()
            }
            fadeInLatestVideosAsSpaceOpens(latestVideosContainer, targetHeight, heightDuration)
        }
    }

    private fun collapseLatestVideos(latestVideosContainer: LinearLayout, wasExpanded: Boolean) {
        if (!wasExpanded || latestVideosContainer.childCount == 0) {
            latestVideosContainer.removeAllViews()
            latestVideosContainer.visibility = View.GONE
            latestVideosContainer.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            return
        }

        val easing = DecelerateInterpolator()
        val rowCount = latestVideosContainer.childCount
        for (index in rowCount - 1 downTo 0) {
            latestVideosContainer.getChildAt(index).animate()
                .cancel()
            latestVideosContainer.getChildAt(index).animate()
                .alpha(0.0f)
                .translationY(6f * latestVideosContainer.resources.displayMetrics.density)
                .setStartDelay((rowCount - 1 - index) * 65L)
                .setDuration(130L)
                .setInterpolator(easing)
                .start()
        }

        latestVideosContainer.postDelayed({
            val startHeight = latestVideosContainer.height
                .takeIf { it > 0 } ?: measureExpandedHeight(latestVideosContainer)
            animateHeight(latestVideosContainer, startHeight, 0, 320L) {
                latestVideosContainer.removeAllViews()
                latestVideosContainer.visibility = View.GONE
                latestVideosContainer.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            }
        }, 90L)
    }

    private fun populateLatestVideos(latestVideosContainer: LinearLayout, startHidden: Boolean) {
        latestVideosContainer.removeAllViews()
        val inflater = LayoutInflater.from(latestVideosContainer.context)
        latestVideos.forEach { stream ->
            val videoView = inflater.inflate(R.layout.list_stream_item, latestVideosContainer, false)
            videoView.findViewById<TextView>(R.id.itemVideoTitleView).text = stream.name
            videoView.findViewById<TextView>(R.id.itemUploaderView).text = stream.uploaderName
            videoView.findViewById<TextView>(R.id.itemAdditionalDetails).text = getStreamDetailLine(
                latestVideosContainer.context,
                stream
            )

            val durationView = videoView.findViewById<TextView>(R.id.itemDurationView)
            durationView.isVisible = stream.duration > 0
            if (stream.duration > 0) {
                durationView.text = Localization.getDurationString(stream.duration)
            }
            videoView.findViewById<View>(R.id.itemProgressView).visibility = View.GONE
            CoilHelper.loadThumbnail(videoView.findViewById(R.id.itemThumbnailView), stream.thumbnails)
            videoView.setOnClickListener { latestVideoClickListener?.invoke(stream) }
            videoView.alpha = if (startHidden) 0.0f else 1.0f
            videoView.translationY = if (startHidden) {
                8f * latestVideosContainer.resources.displayMetrics.density
            } else {
                0.0f
            }
            latestVideosContainer.addView(videoView)
        }
    }

    private fun fadeInLatestVideosAsSpaceOpens(
        latestVideosContainer: LinearLayout,
        targetHeight: Int,
        heightDuration: Long
    ) {
        val easing = DecelerateInterpolator()
        if (targetHeight <= 0) {
            return
        }
        for (index in 0 until latestVideosContainer.childCount) {
            val child = latestVideosContainer.getChildAt(index)
            child.animate().cancel()
            val childBottom = child.bottom.takeIf { it > 0 }
                ?: ((index + 1) * targetHeight / latestVideosContainer.childCount)
            val startDelay = ((childBottom.toFloat() / targetHeight) * heightDuration)
                .toLong()
                .coerceAtLeast(70L)
            child.animate()
                .alpha(1.0f)
                .translationY(0.0f)
                .setStartDelay(startDelay)
                .setDuration(170L)
                .setInterpolator(easing)
                .start()
        }
    }

    private fun measureExpandedHeight(view: LinearLayout): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return view.measuredHeight
    }

    private fun animateHeight(
        view: LinearLayout,
        start: Int,
        end: Int,
        duration: Long,
        endAction: () -> Unit
    ) {
        ValueAnimator.ofInt(start, end).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                view.layoutParams.height = it.animatedValue as Int
                view.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = endAction()
            })
            start()
        }
    }

    private fun getStreamDetailLine(context: Context, stream: StreamInfoItem): String {
        val details = mutableListOf<String>()
        if (stream.viewCount >= 0) {
            details.add(Localization.shortViewCount(context, stream.viewCount))
        }
        stream.uploadDate?.let {
            details.add(Localization.relativeTime(it.offsetDateTime()))
        } ?: stream.textualUploadDate?.takeIf { it.isNotEmpty() }?.let(details::add)

        return details.joinToString(Localization.DOT_SEPARATOR)
    }

    private fun getDetailLine(context: Context): String {
        var details = if (infoItem.subscriberCount >= 0) {
            Localization.shortSubscriberCount(context, infoItem.subscriberCount)
        } else {
            context.getString(R.string.subscribers_count_not_available)
        }

        if (itemVersion == ItemVersion.NORMAL && infoItem.streamCount >= 0) {
            val formattedVideoAmount = Localization.localizeStreamCount(context, infoItem.streamCount)
            details = Localization.concatenateStrings(details, formattedVideoAmount)
        }
        return details
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int {
        return if (itemVersion == ItemVersion.GRID) 1 else spanCount
    }
}
