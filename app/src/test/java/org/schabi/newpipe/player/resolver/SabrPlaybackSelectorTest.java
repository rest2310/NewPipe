package org.schabi.newpipe.player.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SabrPlaybackSelectorTest {

    @Test
    public void sabrWinsWhenClassicIsCappedAt360p() {
        assertFalse(SabrPlaybackSelector.hasClassicVideoAbove360p(Arrays.asList(
                video("classic", "360p", DeliveryMethod.PROGRESSIVE_HTTP),
                video("sabr", "2160p", DeliveryMethod.SABR)), Collections.emptyList()));
    }

    @Test
    public void classicWinsWhenItOffersMoreThan360p() {
        assertTrue(SabrPlaybackSelector.hasClassicVideoAbove360p(Collections.singletonList(
                video("classic", "720p", DeliveryMethod.PROGRESSIVE_HTTP)),
                Collections.singletonList(video("sabr", "2160p", DeliveryMethod.SABR))));
    }

    @Test
    public void deliverySelectionNeverMixesSabrAndClassic() {
        final List<VideoStream> streams = Arrays.asList(
                video("classic", "360p", DeliveryMethod.PROGRESSIVE_HTTP),
                video("sabr", "360p", DeliveryMethod.SABR));

        assertEquals("sabr", SabrPlaybackSelector.selectDelivery(streams, true).get(0).getId());
        assertEquals("classic",
                SabrPlaybackSelector.selectDelivery(streams, false).get(0).getId());
    }

    private static VideoStream video(final String id,
                                     final String resolution,
                                     final DeliveryMethod delivery) {
        return new VideoStream.Builder()
                .setId(id)
                .setContent("", delivery != DeliveryMethod.SABR)
                .setIsVideoOnly(true)
                .setResolution(resolution)
                .setMediaFormat(MediaFormat.WEBM)
                .setDeliveryMethod(delivery)
                .build();
    }
}
