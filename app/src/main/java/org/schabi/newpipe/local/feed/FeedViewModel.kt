package org.schabi.newpipe.local.feed

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.functions.Function4
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.App
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT

class FeedViewModel(
    private val application: Application,
    groupId: Long = FeedGroupEntity.GROUP_ALL_ID
) : ViewModel() {
    private val feedDatabaseManager = FeedDatabaseManager(application)

    private val feedFilter = BehaviorProcessor.create<FeedFilter>()
    private val feedFilterFlowable = feedFilter
        .startWithItem(FeedFilter.ALL)
        .distinctUntilChanged()

    private val mutableStateLiveData = MutableLiveData<FeedState>()
    val stateLiveData: LiveData<FeedState> = mutableStateLiveData

    private var combineDisposable = Flowable
        .combineLatest(
            FeedEventManager.events(),
            feedFilterFlowable,
            feedDatabaseManager.notLoadedCount(groupId),
            feedDatabaseManager.oldestSubscriptionUpdate(groupId),

            Function4 {
                    t1: FeedEventManager.Event,
                    t2: FeedFilter,
                    t3: Long,
                    t4: List<OffsetDateTime?>
                ->
                return@Function4 CombineResultEventHolder(t1, t2, t3, t4.firstOrNull())
            }
        )
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .map { holder ->
            val streamItems = if (holder.t1 is SuccessResultEvent || holder.t1 is IdleEvent) {
                feedDatabaseManager
                    .getStreams(groupId, holder.t2)
                    .blockingGet(arrayListOf())
            } else {
                arrayListOf()
            }

            CombineResultDataHolder(holder.t1, streamItems, holder.t3, holder.t4)
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { (event, listFromDB, notLoadedCount, oldestUpdate) ->
            mutableStateLiveData.postValue(
                when (event) {
                    is IdleEvent -> FeedState.LoadedState(listFromDB.map { e -> StreamItem(e) }, oldestUpdate, notLoadedCount, listOf())
                    is ProgressEvent -> FeedState.ProgressState(event.currentProgress, event.maxProgress, event.progressMessage)
                    is SuccessResultEvent -> FeedState.LoadedState(listFromDB.map { e -> StreamItem(e) }, oldestUpdate, notLoadedCount, event.itemsErrors)
                    is ErrorResultEvent -> FeedState.ErrorState(event.error)
                }
            )

            if (event is ErrorResultEvent || event is SuccessResultEvent) {
                FeedEventManager.reset()
            }
        }

    override fun onCleared() {
        super.onCleared()
        combineDisposable.dispose()
    }

    private data class CombineResultEventHolder(
        val t1: FeedEventManager.Event,
        val t2: FeedFilter,
        val t3: Long,
        val t4: OffsetDateTime?
    )

    private data class CombineResultDataHolder(
        val t1: FeedEventManager.Event,
        val t2: List<StreamWithState>,
        val t3: Long,
        val t4: OffsetDateTime?
    )

    fun setFeedFilter(filter: FeedFilter) {
        feedFilter.onNext(filter)
    }

    companion object {
        fun getFactory(@Suppress("UNUSED_PARAMETER") context: Context, groupId: Long) = viewModelFactory {
            initializer {
                FeedViewModel(
                    App.instance,
                    groupId
                )
            }
        }
    }
}
