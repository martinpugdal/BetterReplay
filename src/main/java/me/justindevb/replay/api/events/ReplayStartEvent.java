package me.justindevb.replay.api.events;

import me.justindevb.replay.ReplaySession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


public class ReplayStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player viewer;
    private final ReplaySession session;

    public ReplayStartEvent(@NotNull Player viewer, @NotNull ReplaySession session) {
        this.viewer = viewer;
        this.session = session;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     *
     * @return Who is watching the replay
     */
    public @NotNull Player getViewer() {
        return viewer;
    }

    public @NotNull ReplaySession getSession() {
        return session;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

}

