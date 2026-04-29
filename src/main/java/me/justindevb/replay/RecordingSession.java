package me.justindevb.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.justindevb.replay.recording.EntityTracker;
import me.justindevb.replay.recording.RecordingEventHandler;
import me.justindevb.replay.recording.RecordingPacketHandler;
import me.justindevb.replay.recording.TimelineBuilder;
import me.justindevb.replay.recording.TimelineEvent;
import me.justindevb.replay.snapshot.WorldSnapshotter;
import me.justindevb.replay.util.model.ReplayObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import static me.justindevb.replay.util.io.ItemStackSerializer.serializeItem;

/**
 * Coordinates a recording session. Owns the tick loop and delegates event handling,
 * entity tracking, and timeline building to focused components in the recording package.
 */
public class RecordingSession {

    private static final int INVENTORY_CHECK_INTERVAL = 5;
    private final Replay replay;
    private final String name;
    private final File file;
    private final EntityTracker tracker;
    private final TimelineBuilder builder;
    private final RecordingEventHandler eventHandler;
    private final RecordingPacketHandler packetHandler;
    private final WorldSnapshotter snapshotter;
    private final Map<UUID, List<String>> lastInventorySnapshot = new HashMap<>();
    private PacketListenerCommon packetListenerHandle;
    private int tick = 0;
    private int durationTicks = -1;
    private boolean stopped = false;

    public RecordingSession(String name, File folder, Collection<Player> players, int durationSeconds) {
        this.name = name;
        this.file = new File(folder, "replays/" + name + ".json");
        this.durationTicks = durationSeconds > 0 ? durationSeconds * 20 : -1;
        this.replay = Replay.getInstance();

        org.bukkit.configuration.file.FileConfiguration cfg = replay != null ? replay.getConfig() : null;
        boolean snapshotEnabled = cfg != null && cfg.getBoolean("WorldSnapshot.Enabled", true);
        int radiusChunks = cfg != null ? cfg.getInt("WorldSnapshot.Radius-Chunks", 1) : 1;
        int maxChunks = cfg != null ? cfg.getInt("WorldSnapshot.Max-Chunks-Per-Session", 256) : 256;
        boolean captureNonPlayer = cfg != null && cfg.getBoolean("WorldSnapshot.Capture-Non-Player-Events", true);

        this.snapshotter = snapshotEnabled
            ? new WorldSnapshotter(radiusChunks, maxChunks,
            replay.getLogger())
            : null;

        this.tracker = new EntityTracker(players);
        this.builder = new TimelineBuilder();
        this.eventHandler = new RecordingEventHandler(
            tracker,
            builder,
            this::getTick,
            lastInventorySnapshot::remove,
            (snapshotter != null && captureNonPlayer) ? snapshotter::isInsideSnapshot : null
        );
        this.packetHandler = new RecordingPacketHandler(tracker, builder, this::getTick);
    }

    public void start() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        Bukkit.getLogger().info("Started recording: " + name + " for " + tracker.getTrackedPlayers().size()
            + " player(s), duration=" + (durationTicks == -1 ? "∞" : durationTicks / 20 + "s"));

        Bukkit.getPluginManager().registerEvents(eventHandler, replay);
        packetListenerHandle = PacketEvents.getAPI().getEventManager().registerListener(packetHandler, PacketListenerPriority.NORMAL);

        captureInitialInventory();
        captureInitialSnapshot();
    }

    /**
     * Called every tick by RecorderManager
     */
    public void tick() {
        if (stopped) return;

        if (durationTicks != -1 && tick >= durationTicks) {
            stop(true);
            return;
        }

        for (UUID uuid : tracker.getTrackedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Location loc = p.getLocation();

            builder.addEvent(new TimelineEvent.PlayerMove(
                tick,
                uuid.toString(),
                p.getName(),
                p.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                p.getPose().name()
            ));

            if (snapshotter != null) {
                snapshotter.expandAround(p);
            }
        }

        for (Map.Entry<UUID, EntityType> entry : tracker.getTrackedEntities().entrySet()) {
            UUID uuid = entry.getKey();
            Entity e = Bukkit.getEntity(uuid);
            if (e == null || e.isDead()) continue;

            Location entityLoc = e.getLocation();

            builder.addEvent(new TimelineEvent.EntityMove(
                tick,
                uuid.toString(),
                e.getType().name(),
                entityLoc.getWorld().getName(),
                entityLoc.getX(), entityLoc.getY(), entityLoc.getZ(),
                entityLoc.getYaw(), entityLoc.getPitch()
            ));
        }

        if (tick % INVENTORY_CHECK_INTERVAL == 0) {
            tickInventoryCheck();
        }

        tick++;
    }

    private void tickInventoryCheck() {
        for (UUID uuid : tracker.getTrackedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            ItemStack[] inventoryContents = p.getInventory().getContents();
            ItemStack[] armorContents = p.getInventory().getArmorContents();
            List<String> currentSerialized = new ArrayList<>(2 + inventoryContents.length + armorContents.length);
            currentSerialized.add(String.valueOf(p.getInventory().getHeldItemSlot()));
            for (ItemStack item : inventoryContents) {
                currentSerialized.add(serializeItem(item));
            }
            currentSerialized.add(serializeItem(p.getInventory().getItemInOffHand()));
            for (ItemStack armor : armorContents) {
                currentSerialized.add(serializeItem(armor));
            }

            List<String> previous = lastInventorySnapshot.get(uuid);
            if (currentSerialized.equals(previous)) continue;

            lastInventorySnapshot.put(uuid, currentSerialized);

            builder.addEvent(builder.captureInventory(tick, uuid.toString(), p));
        }
    }

    public void stop(boolean save) {
        if (stopped) return;
        stopped = true;
        HandlerList.unregisterAll(eventHandler);
        if (packetListenerHandle != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListenerHandle);
            packetListenerHandle = null;
        }

        tracker.clearPlayers();

        if (!save) return;

        ReplayObject replayObject = new ReplayObject(
            name,
            builder.getTimeline(),
            snapshotter != null ? snapshotter.getSnapshot() : null,
            replay.getReplayStorage()
        );

        replayObject.save()
            .thenCompose(v ->
                replay.getReplayStorage().listReplays()
            )
            .thenAccept(replays -> {
                replay.getReplayCache().setReplays(replays);
                replay.getLogger().info("Recording " + name + " saved!");
            })
            .exceptionally(ex -> {
                replay.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save recording: " + name, ex);
                return null;
            });
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getTick() {
        return tick;
    }

    public List<TimelineEvent> getTimeline() {
        return builder.getTimeline();
    }

    public Set<UUID> getTrackedPlayers() {
        return tracker.getTrackedPlayers();
    }

    public boolean isTrackedPlayer(UUID uuid) {
        return tracker.isTrackedPlayer(uuid);
    }

    private void captureInitialInventory() {
        for (UUID uuid : tracker.getTrackedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            builder.addEvent(builder.captureInventory(tick, uuid.toString(), p));
        }
    }

    private void captureInitialSnapshot() {
        if (snapshotter == null) return;
        for (UUID uuid : tracker.getTrackedPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            snapshotter.expandAround(p);
        }
    }
}
