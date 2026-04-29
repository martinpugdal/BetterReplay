package me.justindevb.replay.api.events;

import java.util.Collection;
import me.justindevb.replay.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RecordingStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String recordingName;
    private final Collection<Player> targets;
    private final RecordingSession session;
    private final int durationSeconds;

    public RecordingStartEvent(@NotNull String recordingName, @NotNull Collection<Player> targets, @NotNull RecordingSession session, int durationSeconds) {
        this.recordingName = recordingName;
        this.targets = targets;
        this.session = session;
        this.durationSeconds = durationSeconds;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public @NotNull String getRecordingName() {
        return recordingName;
    }

    public @NotNull Collection<Player> getTargets() {
        return targets;
    }

    public @NotNull RecordingSession getSession() {
        return session;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

}

