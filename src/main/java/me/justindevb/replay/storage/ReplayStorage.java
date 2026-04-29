package me.justindevb.replay.storage;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.justindevb.replay.recording.TimelineEvent;

public interface ReplayStorage {

    // -- Timeline-only API (legacy) --

    CompletableFuture<Void> saveReplay(String name, List<TimelineEvent> timeline);

    CompletableFuture<List<TimelineEvent>> loadReplay(String name);

    // -- ReplayData API (timeline + optional world snapshot) --

    /**
     * Persist a replay including timeline and (optional) world snapshot.
     * Implementations should override this when they support snapshot storage.
     * The default implementation forwards to the timeline-only {@link #saveReplay(String, List)}
     * and discards the snapshot.
     */
    default CompletableFuture<Void> saveReplay(String name, ReplayData data) {
        return saveReplay(name, data != null ? data.timeline() : null);
    }

    /**
     * Load a replay's full payload (timeline + optional world snapshot).
     * Implementations should override this when they support snapshot storage.
     * The default implementation wraps {@link #loadReplay(String)} and reports
     * a {@code null} snapshot.
     */
    default CompletableFuture<ReplayData> loadReplayData(String name) {
        return loadReplay(name).thenApply(timeline -> timeline != null ? new ReplayData(timeline, null) : null);
    }

    CompletableFuture<List<String>> listReplays();

    CompletableFuture<Boolean> deleteReplay(String name);

    CompletableFuture<Boolean> replayExists(String name);

    CompletableFuture<File> getReplayFile(String name);
}
