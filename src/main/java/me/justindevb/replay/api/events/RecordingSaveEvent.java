package me.justindevb.replay.api.events;

import java.util.List;
import me.justindevb.replay.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RecordingSaveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RecordingSession session;
    private boolean cancelled = false;

    public RecordingSaveEvent(String recordingName, List<Player> targets, RecordingSession session) {
        this.session = session;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public RecordingSession getSession() {
        return session;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
