package us.shandian.giga.get;

import androidx.annotation.NonNull;

public class FinishedMission extends Mission {

    public FinishedMission() {
    }

    public FinishedMission(@NonNull DownloadMission mission) {
        source = mission.source;
        title = mission.title;
        uploaderName = mission.uploaderName;
        thumbnailUrl = mission.thumbnailUrl;
        textualUploadDate = mission.textualUploadDate;
        viewCount = mission.viewCount;
        uploadDateMillis = mission.uploadDateMillis;
        durationSeconds = mission.durationSeconds;
        length = mission.length;
        timestamp = mission.timestamp;
        kind = mission.kind;
        storage = mission.storage;
    }

}
