package org.schabi.newpipe.player.resolver;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SabrPlaybackSelector {
    private static final int CLASSIC_QUALITY_CEILING = 360;

    private SabrPlaybackSelector() {
    }

    static boolean shouldUseSabr(@NonNull final Context context,
                                 @NonNull final StreamInfo info) {
        if (info.getServiceId() != ServiceList.YouTube.getServiceId()
                || info.getStreamType() != StreamType.VIDEO_STREAM
                || !hasSabrPair(info)) {
            return false;
        }
        final boolean forced = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.force_sabr_protocol_key), false);
        return forced || !hasClassicVideoAbove360p(info.getVideoStreams(),
                info.getVideoOnlyStreams());
    }

    static boolean hasClassicVideoAbove360p(@Nullable final List<VideoStream> videoStreams,
                                            @Nullable final List<VideoStream> videoOnlyStreams) {
        return hasClassicVideoAbove360p(videoStreams)
                || hasClassicVideoAbove360p(videoOnlyStreams);
    }

    private static boolean hasClassicVideoAbove360p(
            @Nullable final List<VideoStream> streams) {
        if (streams == null) {
            return false;
        }
        for (final VideoStream stream : streams) {
            if (stream.getDeliveryMethod() == DeliveryMethod.SABR) {
                continue;
            }
            final ItagItem itag = stream.getItagItem();
            if (itag != null && itag.getHeight() > CLASSIC_QUALITY_CEILING) {
                return true;
            }
            if (itag == null && resolutionHeight(stream.getResolution())
                    > CLASSIC_QUALITY_CEILING) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    static <S extends Stream> List<S> selectDelivery(@Nullable final List<S> streams,
                                                     final boolean useSabr) {
        if (streams == null || streams.isEmpty()) {
            return Collections.emptyList();
        }
        final List<S> selected = new ArrayList<>();
        for (final S stream : streams) {
            if ((stream.getDeliveryMethod() == DeliveryMethod.SABR) == useSabr) {
                selected.add(stream);
            }
        }
        return selected;
    }

    private static boolean hasSabrPair(@NonNull final StreamInfo info) {
        boolean hasVideo = false;
        for (final VideoStream stream : allVideoStreams(info)) {
            hasVideo |= stream.getDeliveryMethod() == DeliveryMethod.SABR;
        }
        boolean hasAudio = false;
        for (final AudioStream stream : info.getAudioStreams()) {
            hasAudio |= stream.getDeliveryMethod() == DeliveryMethod.SABR;
        }
        return hasVideo && hasAudio;
    }

    @NonNull
    private static List<VideoStream> allVideoStreams(@NonNull final StreamInfo info) {
        final List<VideoStream> streams = new ArrayList<>();
        if (info.getVideoStreams() != null) {
            streams.addAll(info.getVideoStreams());
        }
        if (info.getVideoOnlyStreams() != null) {
            streams.addAll(info.getVideoOnlyStreams());
        }
        return streams;
    }

    private static int resolutionHeight(@Nullable final String resolution) {
        if (resolution == null) {
            return -1;
        }
        final int p = resolution.indexOf('p');
        if (p <= 0) {
            return -1;
        }
        try {
            return Integer.parseInt(resolution.substring(0, p));
        } catch (final NumberFormatException ignored) {
            return -1;
        }
    }
}
