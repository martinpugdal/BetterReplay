package me.justindevb.replay.playback;

import me.justindevb.replay.Replay;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Captures and restores the viewer's pre-replay state (invulnerability and item-pickup flags),
 * and stops the session when the viewer disconnects mid-replay.
 *
 * <p>State capture must happen before {@link #applyReplayState()} so the original values
 * can be restored on stop. For nested replays, {@link #inheritStateFrom(ReplayViewerManager)}
 * preserves the originals across sessions.</p>
 */
public class ReplayViewerManager implements Listener {

    private final Player viewer;
    private final SessionControl sessionControl;

    private boolean originalInvulnerable;
    private boolean originalCanPickupItems;

    public ReplayViewerManager(Player viewer, Replay replay, SessionControl sessionControl) {
        this.viewer = viewer;
        this.sessionControl = sessionControl;
        Bukkit.getPluginManager().registerEvents(this, replay);
    }

    public void captureState() {
        this.originalInvulnerable = viewer.isInvulnerable();
        this.originalCanPickupItems = viewer.getCanPickupItems();
    }

    public void inheritStateFrom(ReplayViewerManager other) {
        this.originalInvulnerable = other.originalInvulnerable;
        this.originalCanPickupItems = other.originalCanPickupItems;
    }

    public void applyReplayState() {
        viewer.setInvulnerable(true);
        viewer.setCanPickupItems(false);
    }

    public void restoreState() {
        viewer.setInvulnerable(originalInvulnerable);
        viewer.setCanPickupItems(originalCanPickupItems);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(viewer)) {
            sessionControl.stop();
        }
    }
}
