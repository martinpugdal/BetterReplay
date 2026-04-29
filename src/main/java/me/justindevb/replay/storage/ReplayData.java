package me.justindevb.replay.storage;

import java.util.List;
import me.justindevb.replay.recording.TimelineEvent;
import me.justindevb.replay.snapshot.WorldSnapshot;

/**
 * Container for a saved replay: the event timeline plus an optional world
 * snapshot that preserves the surroundings even if the live world is later
 * changed (e.g. a regenerating mine).
 */
public record ReplayData(List<TimelineEvent> timeline, WorldSnapshot worldSnapshot) {

    /**
     * May be {@code null} for legacy replays recorded before snapshot support.
     */
    @Override
    public WorldSnapshot worldSnapshot() {
        return worldSnapshot;
    }

    public boolean isEmpty() {
        return timeline == null || timeline.isEmpty();
    }
}
