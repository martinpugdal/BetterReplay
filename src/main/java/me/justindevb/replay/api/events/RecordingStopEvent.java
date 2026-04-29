package me.justindevb.replay.api.events;

import me.justindevb.replay.RecordingSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RecordingStopEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RecordingSession session;

    public RecordingStopEvent(RecordingSession session) {
        this.session = session;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public RecordingSession getSession() {
        return session;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
