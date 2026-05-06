package org.schabi.newpipe.local.subscription

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var subscriptionManager = SubscriptionManager(application)

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData

    private val expandedChannels = mutableSetOf<String>()
    private val loadingLatestVideos = mutableSetOf<String>()
    private val latestVideosCache = mutableMapOf<String, List<StreamInfoItem>>()
    private val latestVideosDisposables = mutableMapOf<String, Disposable>()
    private val actionDisposables = CompositeDisposable()

    private var stateItemsDisposable = subscriptionManager.subscriptions()
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { subscriptions ->
            subscriptions.map { entity ->
                val channelInfoItem = entity.toChannelInfoItem()
                val key = channelKey(channelInfoItem.serviceId, channelInfoItem.url)
                ChannelItem(
                    infoItem = channelInfoItem,
                    subscriptionId = entity.uid,
                    isFavorite = entity.isFavorite,
                    isExpanded = expandedChannels.contains(key),
                    isLoadingLatestVideos = loadingLatestVideos.contains(key),
                    latestVideos = latestVideosCache[key].orEmpty(),
                    itemVersion = ChannelItem.ItemVersion.MINI
                )
            }
        }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableStateLiveData.postValue(SubscriptionState.LoadedState(it)) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    override fun onCleared() {
        super.onCleared()
        stateItemsDisposable.dispose()
        latestVideosDisposables.values.forEach(Disposable::dispose)
        actionDisposables.dispose()
    }

    fun setFavorite(infoItem: ChannelInfoItem, isFavorite: Boolean) {
        actionDisposables.add(
            subscriptionManager.setFavorite(infoItem.serviceId, infoItem.url, isFavorite)
                .subscribe({}, { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) })
        )
    }

    fun toggleExpanded(infoItem: ChannelInfoItem) {
        val key = channelKey(infoItem.serviceId, infoItem.url)
        if (!expandedChannels.add(key)) {
            expandedChannels.remove(key)
            emitCurrentItemsWithExpansionState()
            return
        }

        if (latestVideosCache.containsKey(key)) {
            emitCurrentItemsWithExpansionState()
            return
        }

        loadingLatestVideos.add(key)
        emitCurrentItemsWithExpansionState()
        loadLatestVideos(infoItem, key)
    }

    private fun loadLatestVideos(infoItem: ChannelInfoItem, key: String) {
        if (latestVideosDisposables[key]?.isDisposed == false) {
            return
        }
        latestVideosDisposables[key] = ExtractorHelper.getChannelInfo(infoItem.serviceId, infoItem.url, false)
            .flatMap { channelInfo ->
                val tab = channelInfo.tabs.firstOrNull()
                if (tab == null) {
                    Single.just<List<StreamInfoItem>>(emptyList())
                } else {
                    ExtractorHelper.getChannelTab(infoItem.serviceId, tab, false)
                        .map { tabInfo ->
                            tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().take(3)
                        }
                }
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { videos ->
                    latestVideosDisposables.remove(key)
                    latestVideosCache[key] = videos
                    loadingLatestVideos.remove(key)
                    emitCurrentItemsWithExpansionState()
                },
                { error ->
                    latestVideosDisposables.remove(key)
                    loadingLatestVideos.remove(key)
                    emitCurrentItemsWithExpansionState()
                    mutableStateLiveData.postValue(SubscriptionState.ErrorState(error))
                }
            )
    }

    private fun emitCurrentItemsWithExpansionState() {
        val currentState = mutableStateLiveData.value as? SubscriptionState.LoadedState ?: return
        mutableStateLiveData.postValue(
            currentState.copy(
                subscriptions = currentState.subscriptions.map { group ->
                    if (group is ChannelItem) {
                        val key = channelKey(group.infoItem.serviceId, group.infoItem.url)
                        ChannelItem(
                            infoItem = group.infoItem,
                            subscriptionId = group.id,
                            isFavorite = group.isFavorite,
                            isExpanded = expandedChannels.contains(key),
                            isLoadingLatestVideos = loadingLatestVideos.contains(key),
                            latestVideos = latestVideosCache[key].orEmpty(),
                            itemVersion = group.itemVersion
                        )
                    } else {
                        group
                    }
                }
            )
        )
    }

    private fun channelKey(serviceId: Int, url: String?): String = "$serviceId:$url"

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }

    companion object {

        /**
         * Returns whether to use GridLayout mode for Subscription Fragment.
         *
         * ### Current mapping:
         *
         *  | ItemViewMode | ItemVersion | Span count |
         *  |---|---|---|
         *  | AUTO | MINI | 1 |
         *  | LIST | MINI | 1 |
         *  | CARD | GRID | > 1 (ThemeHelper defined) |
         *  | GRID | GRID | > 1 (ThemeHelper defined) |
         *
         *  @see [SubscriptionViewModel.shouldUseGridForSubscription] to modify Layout Manager
         */
        fun shouldUseGridForSubscription(context: Context): Boolean {
            val itemViewMode = getItemViewMode(context)
            return itemViewMode == ItemViewMode.GRID || itemViewMode == ItemViewMode.CARD
        }
    }
}
