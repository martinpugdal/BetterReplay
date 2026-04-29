package me.justindevb.replay.util.model;


import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.justindevb.replay.recording.TimelineEvent;
import me.justindevb.replay.snapshot.WorldSnapshot;
import me.justindevb.replay.storage.ReplayData;
import me.justindevb.replay.storage.ReplayStorage;

public class ReplayObject {

    private final String name;
    private final ReplayStorage storage;
    private List<TimelineEvent> timeline;
    private WorldSnapshot worldSnapshot;

    public ReplayObject(String name, List<TimelineEvent> timeline, ReplayStorage storage) {
        this(name, timeline, null, storage);
    }

    public ReplayObject(String name, List<TimelineEvent> timeline, WorldSnapshot worldSnapshot, ReplayStorage storage) {
        this.name = name;
        this.timeline = timeline;
        this.worldSnapshot = worldSnapshot;
        this.storage = storage;
    }

    public String getName() {
        return name;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineEvent> timeline) {
        this.timeline = timeline;
    }

    public WorldSnapshot getWorldSnapshot() {
        return worldSnapshot;
    }

    public void setWorldSnapshot(WorldSnapshot worldSnapshot) {
        this.worldSnapshot = worldSnapshot;
    }

    /**
     * Saves this replay using the configured storage asynchronously
     */
    public CompletableFuture<Void> save() {
        if (worldSnapshot != null && !worldSnapshot.isEmpty()) {
            return storage.saveReplay(name, new ReplayData(timeline, worldSnapshot));
        }
        // Legacy timeline-only path; preserves the older interface contract for callers
        // (and tests) that don't carry a world snapshot.
        return storage.saveReplay(name, timeline);
    }

    /**
     * Loads the timeline (and any persisted world snapshot) from storage and updates this object
     */
    public CompletableFuture<Void> load() {
        return storage.loadReplayData(name)
            .thenAccept(loaded -> {
                if (loaded != null) {
                    this.timeline = loaded.timeline();
                    this.worldSnapshot = loaded.worldSnapshot();
                }
            });
    }

    /**
     * Deletes this replay from storage
     */
    public CompletableFuture<Boolean> delete() {
        return storage.deleteReplay(name);
    }

    /**
     * Check if this replay exists in storage
     */
    public CompletableFuture<Boolean> exists() {
        return storage.replayExists(name);
    }
}

