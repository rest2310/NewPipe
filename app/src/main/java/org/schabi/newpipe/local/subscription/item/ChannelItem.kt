package org.schabi.newpipe.local.subscription.item

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
        val subscribedButton = viewHolder.root.findViewById<View?>(R.id.subscribedButton) as? MaterialButton
        val expandButton = viewHolder.root.findViewById<View?>(R.id.expandButton) as? ImageButton
        val latestVideosContainer = viewHolder.root.findViewById<View?>(R.id.latestVideosContainer) as? LinearLayout
        val latestVideosProgress = viewHolder.root.findViewById<View?>(R.id.latestVideosProgress) as? ProgressBar
        val latestVideoViews = listOfNotNull(
            viewHolder.root.findViewById<View?>(R.id.latestVideo1) as? TextView,
            viewHolder.root.findViewById<View?>(R.id.latestVideo2) as? TextView,
            viewHolder.root.findViewById<View?>(R.id.latestVideo3) as? TextView
        )

        itemTitleView.text = infoItem.name
        itemAdditionalDetails.text = getDetailLine(viewHolder.root.context)
        if (itemVersion == ItemVersion.NORMAL) {
            itemChannelDescriptionView?.text = infoItem.description
        }

        CoilHelper.loadAvatar(itemThumbnailView, infoItem.thumbnails)
        bindSubscriptionActions(favoriteButton, subscribedButton, expandButton)
        bindLatestVideos(latestVideosContainer, latestVideosProgress, latestVideoViews)

        gesturesListener?.run {
            viewHolder.root.setOnClickListener { selected(infoItem) }
            viewHolder.root.setOnLongClickListener {
                held(infoItem)
                true
            }
        }
    }

    private fun bindSubscriptionActions(
        favoriteButton: ImageButton?,
        subscribedButton: MaterialButton?,
        expandButton: ImageButton?
    ) {
        favoriteButton?.apply {
            alpha = if (isFavorite) 1.0f else 0.35f
            setOnClickListener {
                favoriteClickListener?.invoke(infoItem, !isFavorite)
            }
        }

        subscribedButton?.setOnClickListener {
            unsubscribeClickListener?.invoke(infoItem)
        }

        expandButton?.apply {
            rotation = if (isExpanded) 180.0f else 0.0f
            setOnClickListener {
                expandClickListener?.invoke(infoItem)
            }
        }
    }

    private fun bindLatestVideos(
        latestVideosContainer: LinearLayout?,
        latestVideosProgress: ProgressBar?,
        latestVideoViews: List<TextView>
    ) {
        latestVideosContainer?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        latestVideosProgress?.visibility = if (isExpanded && isLoadingLatestVideos) View.VISIBLE else View.GONE

        latestVideoViews.forEachIndexed { index, textView ->
            val stream = latestVideos.getOrNull(index)
            textView.visibility = if (isExpanded && stream != null) View.VISIBLE else View.GONE
            if (stream != null) {
                textView.text = stream.name
                textView.setOnClickListener { latestVideoClickListener?.invoke(stream) }
            } else {
                textView.setOnClickListener(null)
            }
        }
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
