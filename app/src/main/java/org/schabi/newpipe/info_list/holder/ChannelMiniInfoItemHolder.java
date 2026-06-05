package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.SimpleDialog;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.CoilHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    private final ImageView itemThumbnailView;
    private final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;
    private final TextView itemChannelDescriptionView;
    @Nullable private final ImageButton favoriteButton;
    @Nullable private final TextView subscribedButton;
    @Nullable private final ImageButton expandButton;
    @Nullable private final View latestVideosContainer;
    @Nullable private final View channelInfoClickArea;
    @Nullable private Disposable subscriptionStateDisposable;
    private boolean subscribed;
    private boolean favorite;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemChannelDescriptionView = itemView.findViewById(R.id.itemChannelDescriptionView);
        favoriteButton = itemView.findViewById(R.id.favoriteButton);
        subscribedButton = itemView.findViewById(R.id.subscribedButton);
        expandButton = itemView.findViewById(R.id.expandButton);
        latestVideosContainer = itemView.findViewById(R.id.latestVideosContainer);
        channelInfoClickArea = itemView.findViewById(R.id.channelInfoClickArea);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return;
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.getName());
        itemTitleView.setSelected(true);

        final String detailLine = getDetailLine(item);
        if (detailLine == null) {
            itemAdditionalDetailView.setVisibility(View.GONE);
        } else {
            itemAdditionalDetailView.setVisibility(View.VISIBLE);
            itemAdditionalDetailView.setText(getDetailLine(item));
        }

        CoilHelper.INSTANCE.loadAvatar(itemThumbnailView, item.getThumbnails());
        bindSearchChannelActions(item);

        final View.OnClickListener openChannel = view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        };
        itemView.setOnClickListener(openChannel);
        if (channelInfoClickArea != null) {
            channelInfoClickArea.setOnClickListener(openChannel);
        }

        final View.OnLongClickListener longClick = view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        };
        itemView.setOnLongClickListener(longClick);
        if (channelInfoClickArea != null) {
            channelInfoClickArea.setOnLongClickListener(longClick);
        }

        if (itemChannelDescriptionView != null) {
            // itemChannelDescriptionView will be null in the mini variant
            if (Utils.isBlank(item.getDescription())) {
                itemChannelDescriptionView.setVisibility(View.GONE);
            } else {
                itemChannelDescriptionView.setVisibility(View.VISIBLE);
                itemChannelDescriptionView.setText(item.getDescription());
                // setMaxLines utilize the line space for description if the additional details
                // (sub / video count) are not present.
                // Case1: 2 lines of description + 1 line additional details
                // Case2: 3 lines of description (additionalDetails is GONE)
                itemChannelDescriptionView.setMaxLines(getDescriptionMaxLineCount(detailLine));
            }
        }
    }

    private void bindSearchChannelActions(final ChannelInfoItem item) {
        if (expandButton != null) {
            expandButton.setVisibility(View.GONE);
            expandButton.setOnClickListener(null);
        }
        if (latestVideosContainer != null) {
            latestVideosContainer.setVisibility(View.GONE);
        }
        if (favoriteButton == null || subscribedButton == null || item.getUrl() == null) {
            return;
        }

        if (subscriptionStateDisposable != null) {
            subscriptionStateDisposable.dispose();
        }
        subscriptionStateDisposable = new SubscriptionManager(itemBuilder.getContext())
                .subscriptionTable()
                .getSubscription(item.getServiceId(), item.getUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        entity -> bindSubscriptionState(item, true, entity.isFavorite()),
                        ignored -> bindSubscriptionState(item, false, false),
                        () -> bindSubscriptionState(item, false, false)
                );
    }

    private void bindSubscriptionState(final ChannelInfoItem item, final boolean isSubscribed,
                                       final boolean isFavorite) {
        subscribed = isSubscribed;
        favorite = isFavorite;
        favoriteButton.setImageResource(isFavorite
                ? R.drawable.ic_favorite_active : R.drawable.ic_favorite_inactive);
        subscribedButton.setText(isSubscribed
                ? R.string.subscribed_button_title : R.string.subscribe_button_title);
        subscribedButton.setTextColor(ContextCompat.getColor(itemBuilder.getContext(),
                isSubscribed ? R.color.white : R.color.black));
        subscribedButton.setBackgroundResource(isSubscribed
                ? R.drawable.feed_filter_selected_background
                : R.drawable.feed_filter_unselected_background);

        favoriteButton.setOnClickListener(view -> {
            if (favorite) {
                setFavoriteState(item, false);
            } else if (subscribed) {
                setFavoriteState(item, true);
            } else {
                subscribeToChannel(item, true);
            }
        });

        subscribedButton.setOnClickListener(view -> {
            if (subscribed) {
                confirmUnsubscribe(item);
            } else {
                subscribeToChannel(item, false);
            }
        });
    }

    private void setFavoriteState(final ChannelInfoItem item, final boolean markFavorite) {
        new SubscriptionManager(itemBuilder.getContext())
                .setFavorite(item.getServiceId(), item.getUrl(), markFavorite)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> bindSubscriptionState(item, true, markFavorite), ignored -> { });
    }

    private void subscribeToChannel(final ChannelInfoItem item, final boolean markFavorite) {
        final SubscriptionManager manager = new SubscriptionManager(itemBuilder.getContext());
        final SubscriptionEntity entity = new SubscriptionEntity();
        entity.setServiceId(item.getServiceId());
        entity.setUrl(item.getUrl());
        entity.setName(item.getName());
        entity.setAvatarUrl(ImageStrategy.imageListToDbUrl(item.getThumbnails()));
        entity.setSubscriberCount(item.getSubscriberCount() >= 0
                ? item.getSubscriberCount() : null);
        entity.setDescription(item.getDescription());
        entity.setFavorite(markFavorite);

        Schedulers.io().scheduleDirect(() -> {
            manager.insertSubscription(entity);
            AndroidSchedulers.mainThread().scheduleDirect(() -> {
                Toast.makeText(itemBuilder.getContext(), R.string.you_successfully_subscribed,
                        Toast.LENGTH_SHORT).show();
                bindSubscriptionState(item, true, markFavorite);
            });
        });
    }

    private void confirmUnsubscribe(final ChannelInfoItem item) {
        SimpleDialog.show(
                itemBuilder.getContext(),
                0,
                R.string.remove_subscription_confirmation,
                R.string.no,
                null,
                0,
                null,
                R.string.yes,
                () -> new SubscriptionManager(itemBuilder.getContext())
                        .deleteSubscription(item.getServiceId(), item.getUrl())
                        .subscribe(() -> bindSubscriptionState(item, false, false), ignored -> { })
        );
    }

    /**
     * Returns max number of allowed lines for the description field.
     * @param content additional detail content (video / sub count)
     * @return max line count
     */
    protected int getDescriptionMaxLineCount(@Nullable final String content) {
        return content == null ? 3 : 2;
    }

    @Nullable
    private String getDetailLine(final ChannelInfoItem item) {
        if (item.getStreamCount() >= 0 && item.getSubscriberCount() >= 0) {
            return Localization.concatenateStrings(
                    Localization.shortSubscriberCount(itemBuilder.getContext(),
                            item.getSubscriberCount()),
                    Localization.localizeStreamCount(itemBuilder.getContext(),
                            item.getStreamCount()));
        } else if (item.getStreamCount() >= 0) {
            return Localization.localizeStreamCount(itemBuilder.getContext(),
                    item.getStreamCount());
        } else if (item.getSubscriberCount() >= 0) {
            return Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        } else {
            return null;
        }
    }
}
