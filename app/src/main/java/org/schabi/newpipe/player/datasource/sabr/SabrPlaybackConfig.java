package org.schabi.newpipe.player.datasource.sabr;

import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider;

public final class SabrPlaybackConfig {
    private final String videoId;
    private final Integer selectedAudioItag;
    private final Integer selectedVideoItag;
    private final Localization localization;
    private final ContentCountry contentCountry;
    private final SabrPoTokenProvider poTokenProvider;

    public SabrPlaybackConfig(final String videoId,
                              final Integer selectedAudioItag,
                              final Integer selectedVideoItag,
                              final Localization localization,
                              final ContentCountry contentCountry,
                              final SabrPoTokenProvider poTokenProvider) {
        this.videoId = videoId;
        this.selectedAudioItag = selectedAudioItag;
        this.selectedVideoItag = selectedVideoItag;
        this.localization = localization;
        this.contentCountry = contentCountry;
        this.poTokenProvider = poTokenProvider;
    }

    public String getVideoId() {
        return videoId;
    }

    public Integer getSelectedAudioItag() {
        return selectedAudioItag;
    }

    public Integer getSelectedVideoItag() {
        return selectedVideoItag;
    }

    public Localization getLocalization() {
        return localization;
    }

    public ContentCountry getContentCountry() {
        return contentCountry;
    }

    public SabrPoTokenProvider getPoTokenProvider() {
        return poTokenProvider;
    }
}
