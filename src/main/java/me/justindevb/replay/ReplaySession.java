package me.justindevb.replay;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.justindevb.replay.api.events.ReplayStartEvent;
import me.justindevb.replay.api.events.ReplayStopEvent;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.entity.RecordedEntityFactory;
import me.justindevb.replay.entity.RecordedPlayer;
import me.justindevb.replay.playback.FakeEntityManager;
import me.justindevb.replay.playback.PlaybackEngine;
import me.justindevb.replay.playback.ReplayBlockManager;
import me.justindevb.replay.playback.ReplayInteractionHandler;
import me.justindevb.replay.playback.ReplayInventoryUI;
import me.justindevb.replay.playback.ReplayViewerManager;
import me.justindevb.replay.playback.SessionControl;
import me.justindevb.replay.recording.TimelineEvent;
import me.justindevb.replay.snapshot.WorldSnapshot;
import me.justindevb.replay.storage.ReplayData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Coordinator for a single replay viewing session. Owns the tick loop, timeline,
 * seek logic, and lifecycle. Delegates block state management to
 * {@link ReplayBlockManager}, event dispatch to {@link PlaybackEngine},
 * UI/inventory to {@link ReplayInventoryUI}, fake-entity bookkeeping to
 * {@link FakeEntityManager}, viewer state and disconnect handling to
 * {@link ReplayViewerManager}, and viewer interactions to
 * {@link ReplayInteractionHandler}.
 */
public class ReplaySession {

    private final Player viewer;
    private final Replay replay;
    private final ReplayRegistry replayRegistry;
    private final WorldSnapshot worldSnapshot;
    private final Set<UUID> deadEntities = new HashSet<>();
    private final Map<UUID, RecordedEntity> recordedEntities = new HashMap<>();
    // Delegates
    private final ReplayBlockManager blockManager;
    private final FakeEntityManager fakeEntityManager;
    private final PlaybackEngine playbackEngine;
    private final ReplayInventoryUI inventoryUI;
    private final ReplayViewerManager viewerManager;
    private final ReplayInteractionHandler interactionHandler;
    private WrappedTask replayTask = null;
    private List<TimelineEvent> timeline;
    private int tick = 0;
    private boolean paused = false;
    private boolean stopped = false;

    public ReplaySession(ReplayData data, Player viewer, Replay replay, ReplayRegistry replayRegistry) {
        this(data != null ? data.timeline() : null,
            data != null ? data.worldSnapshot() : null,
            viewer, replay, replayRegistry);
    }

    public ReplaySession(List<TimelineEvent> timeline, WorldSnapshot worldSnapshot, Player viewer, Replay replay, ReplayRegistry replayRegistry) {
        this.timeline = timeline;
        this.worldSnapshot = worldSnapshot;
        this.viewer = viewer;
        this.replay = replay;
        this.replayRegistry = replayRegistry;

        SessionControl sessionControl = new SessionControl() {
            @Override
            public void togglePause() {
                paused = !paused;
            }

            @Override
            public void skipSeconds(int seconds) {
                ReplaySession.this.skipSeconds(seconds);
            }

            @Override
            public void stop() {
                ReplaySession.this.stop();
            }

            @Override
            public boolean isActive() {
                return ReplaySession.this.isActive();
            }
        };

        this.blockManager = new ReplayBlockManager(viewer, replay);
        this.fakeEntityManager = new FakeEntityManager(viewer);
        this.playbackEngine = new PlaybackEngine(deadEntities, recordedEntities, blockManager, fakeEntityManager);
        this.inventoryUI = new ReplayInventoryUI(viewer, () -> recordedEntities, sessionControl);
        this.viewerManager = new ReplayViewerManager(viewer, replay, sessionControl);
        this.interactionHandler = new ReplayInteractionHandler(viewer, replay, () -> recordedEntities, fakeEntityManager);

        Bukkit.getPluginManager().registerEvents(inventoryUI, replay);
    }

    public void start() {
        if (timeline == null || timeline.isEmpty()) {
            viewer.sendMessage("Replay is empty!");
            return;
        }

        ReplaySession existingSession = replayRegistry.getSessionForViewer(viewer);
        replayRegistry.add(this);
        timeline = blockManager.enrichBlockBreakStageTimeline(timeline);
        if (existingSession != null) {
            inventoryUI.transferSavedInventory(existingSession.getInventoryUI());
            viewerManager.inheritStateFrom(existingSession.viewerManager);
        } else {
            inventoryUI.copyInventory();
            viewerManager.captureState();
        }
        viewerManager.applyReplayState();

        TimelineEvent firstLocationEvent = timeline.stream()
            .filter(e -> e instanceof TimelineEvent.PlayerMove || e instanceof TimelineEvent.EntityMove
                || e instanceof TimelineEvent.EntitySpawn)
            .findFirst()
            .orElse(null);

        if (firstLocationEvent != null) {
            Location teleportLoc = switch (firstLocationEvent) {
                case TimelineEvent.PlayerMove e ->
                    new Location(Bukkit.getWorld(e.world()), e.x(), e.y(), e.z(), e.yaw(), e.pitch());
                case TimelineEvent.EntityMove e ->
                    new Location(Bukkit.getWorld(e.world()), e.x(), e.y(), e.z(), e.yaw(), e.pitch());
                case TimelineEvent.EntitySpawn e ->
                    new Location(Bukkit.getWorld(e.world()), e.x(), e.y(), e.z(), 0f, 0f);
                default -> null;
            };
            if (teleportLoc != null && teleportLoc.getWorld() != null) {
                replay.getFoliaLib().getScheduler().teleportAsync(viewer, teleportLoc);
            }
        }

        inventoryUI.giveReplayControls();
        // Apply the captured world snapshot first so unmodified blocks reflect the
        // recording-time appearance even if the live world has been reset since.
        // primeInitialBrokenBlockStates() then refines per-coordinate baselines for
        // blocks that the timeline subsequently mutates.
        blockManager.primeFromSnapshot(worldSnapshot);
        blockManager.primeInitialBrokenBlockStates(timeline);

        Bukkit.getPluginManager().callEvent(new ReplayStartEvent(viewer, this));

        replay.getFoliaLib().getScheduler().runTimer(this::runTickStep, 1L, 1L);
    }

    /**
     * One iteration of the replay tick loop scheduled from {@link #start()}.
     * Package-private so deterministic scheduler-callback harness tests can drive it
     * without needing the full Bukkit setup that {@code start()} requires.
     */
    void runTickStep(WrappedTask task) {
        if (paused) {
            sendActionBar();
            return;
        }
        replayTask = task;

        if (tick >= timeline.size()) {
            task.cancel();
            stop();
            return;
        }

        if (!viewer.isOnline()) {
            task.cancel();
            return;
        }

        TimelineEvent firstEvent = timeline.get(tick);
        int recordedTick = firstEvent.tick();

        while (tick < timeline.size()) {
            TimelineEvent event = timeline.get(tick);
            int eventTick = event.tick();
            if (eventTick != recordedTick) break;

            if (event instanceof TimelineEvent.BlockBreakStage bbs) {
                blockManager.showGlobalBlockBreakStage(bbs);
                tick++;
                continue;
            }

            UUID uuid = parseEventUuidOrNull(event);
            if (uuid == null) {
                tick++;
                continue;
            }

            if (event instanceof TimelineEvent.PlayerQuit) {
                if (recordedEntities.get(uuid) instanceof RecordedPlayer rp) {
                    viewer.sendMessage("[BetterReplay] " + rp.getName() + " disconnected");
                }
                removeAndDestroyRecordedEntity(uuid);
                tick++;
                continue;
            }

            if (deadEntities.contains(uuid)
                && (event instanceof TimelineEvent.PlayerMove || event instanceof TimelineEvent.EntityMove)) {
                tick++;
                continue;
            }

            RecordedEntity recorded = recordedEntities.get(uuid);

            if (recorded != null && recorded.isDestroyed()) {
                recordedEntities.remove(uuid);
                tick++;
                continue;
            }

            if (recorded == null) {
                Location initialLoc = locationFromEvent(event);
                if (initialLoc == null) {
                    tick++;
                    continue;
                }

                recorded = RecordedEntityFactory.create(event, viewer);
                if (recorded == null) {
                    tick++;
                    continue;
                }

                recorded.spawn(initialLoc);
                recordedEntities.put(uuid, recorded);

                if (recorded instanceof RecordedPlayer rp) {
                    TimelineEvent.InventoryUpdate inv = getInventorySnapshotForPlayer(uuid);
                    if (inv != null) rp.updateInventory(inv);
                }
            }

            playbackEngine.handleEvent(recorded, event);
            tick++;
        }
        sendActionBar();
    }

    public void stop() {
        if (stopped) return;
        stopped = true;

        try {
            viewer.sendActionBar(Component.empty());

            Bukkit.getPluginManager().callEvent(new ReplayStopEvent(viewer, this));
            recordedEntities.values().forEach(RecordedEntity::destroy);
            recordedEntities.clear();

            fakeEntityManager.clearAll();
            blockManager.incrementEpoch();
            blockManager.clearAllVisibleBreakStages();
            blockManager.restoreSessionBaseline();
            inventoryUI.restoreInventory();
            viewerManager.restoreState();
            if (replayTask != null) {
                replay.getFoliaLib().getScheduler().cancelTask(replayTask);
                replayTask = null;
            }

            viewer.sendMessage("Replay finished");
        } finally {
            replayRegistry.remove(this);
            HandlerList.unregisterAll(inventoryUI);
            viewerManager.shutdown();
            interactionHandler.shutdown();
        }
    }

    // -- Skip / Seek --

    private void skipSeconds(int seconds) {
        if (timeline == null || timeline.isEmpty()) return;

        int currentIndex = getCurrentTimelineIndex();
        int targetIndex = resolveSeekTargetIndex(currentIndex, seconds);

        if (targetIndex != currentIndex) {
            blockManager.incrementEpoch();
        }

        applyBlockStateForSeek(currentIndex, targetIndex);

        syncEntityStatesAtIndex(targetIndex);
        tick = targetIndex;
        sendActionBar();
    }

    private int getCurrentTimelineIndex() {
        return Math.clamp(tick, 0, timeline.size());
    }

    private int resolveSeekTargetIndex(int currentIndex, int seconds) {
        int currentRecordedTick = currentIndex > 0 ? getRecordedTickAtIndex(currentIndex - 1) : 0;
        int maxRecordedTick = getRecordedTickAtIndex(timeline.size() - 1);
        long targetRecordedTickLong = (long) currentRecordedTick + ((long) seconds * 20L);
        int targetRecordedTick = (int) Math.clamp(targetRecordedTickLong, 0L, maxRecordedTick);
        return findTimelineIndexAfterRecordedTick(targetRecordedTick);
    }

    private void applyBlockStateForSeek(int currentIndex, int targetIndex) {
        if (targetIndex > currentIndex) {
            blockManager.applyReplayBlockChangesInRange(currentIndex, targetIndex, timeline);
        } else if (targetIndex < currentIndex) {
            blockManager.rebuildReplayBlockStateUntil(targetIndex, timeline);
        }
    }

    private void syncEntityStatesAtIndex(int targetIndex) {
        Map<UUID, TimelineEvent> firstEventByUUID = new LinkedHashMap<>();
        Map<UUID, TimelineEvent> lastLocationByUUID = new LinkedHashMap<>();
        Map<UUID, TimelineEvent.InventoryUpdate> lastInventoryByUUID = new LinkedHashMap<>();
        Set<UUID> shouldHaveQuitAtTarget = new HashSet<>();
        Set<UUID> shouldBeDeadAtTarget = new HashSet<>();

        int end = Math.min(targetIndex, timeline.size());
        for (int i = 0; i < end; i++) {
            TimelineEvent event = timeline.get(i);
            UUID uuid = parseEventUuidOrNull(event);
            if (uuid == null) continue;

            firstEventByUUID.putIfAbsent(uuid, event);

            switch (event) {
                case TimelineEvent.PlayerMove ignored2 -> lastLocationByUUID.put(uuid, event);
                case TimelineEvent.EntityMove ignored2 -> lastLocationByUUID.put(uuid, event);
                case TimelineEvent.InventoryUpdate inv -> lastInventoryByUUID.put(uuid, inv);
                case TimelineEvent.PlayerQuit ignored2 -> shouldHaveQuitAtTarget.add(uuid);
                case TimelineEvent.EntityDeath ignored2 -> shouldBeDeadAtTarget.add(uuid);
                default -> {
                }
            }
        }

        deadEntities.clear();
        deadEntities.addAll(shouldBeDeadAtTarget);

        Set<UUID> shouldExistAtTarget = new HashSet<>(firstEventByUUID.keySet());
        shouldExistAtTarget.removeAll(shouldHaveQuitAtTarget);
        shouldExistAtTarget.removeAll(shouldBeDeadAtTarget);

        for (UUID uuid : new HashSet<>(recordedEntities.keySet())) {
            if (!shouldExistAtTarget.contains(uuid)) {
                removeAndDestroyRecordedEntity(uuid);
            }
        }

        for (UUID uuid : shouldExistAtTarget) {
            if (recordedEntities.containsKey(uuid)) continue;
            if (!lastLocationByUUID.containsKey(uuid)) continue;

            TimelineEvent firstEvent = firstEventByUUID.get(uuid);
            TimelineEvent locEvent = lastLocationByUUID.get(uuid);

            Location loc = locationFromEvent(locEvent);
            if (loc == null) continue;

            RecordedEntity entity = RecordedEntityFactory.create(firstEvent, viewer);
            if (entity == null) continue;

            entity.spawn(loc);
            recordedEntities.put(uuid, entity);
            fakeEntityManager.track(entity.getFakeEntityId());
        }

        for (Map.Entry<UUID, TimelineEvent> entry : lastLocationByUUID.entrySet()) {
            RecordedEntity entity = recordedEntities.get(entry.getKey());
            if (entity == null) continue;
            Location loc = locationFromEvent(entry.getValue());
            if (loc == null) continue;
            entity.moveTo(loc);
        }

        for (Map.Entry<UUID, TimelineEvent.InventoryUpdate> entry : lastInventoryByUUID.entrySet()) {
            RecordedEntity entity = recordedEntities.get(entry.getKey());
            if (entity instanceof RecordedPlayer rp) {
                rp.updateInventory(entry.getValue());
            }
        }
    }

    // -- Helpers --

    private UUID parseEventUuidOrNull(TimelineEvent event) {
        String uuidStr = event.uuid();
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void removeAndDestroyRecordedEntity(UUID uuid) {
        RecordedEntity entity = recordedEntities.remove(uuid);
        if (entity == null) return;
        entity.destroy();
        fakeEntityManager.untrack(entity.getFakeEntityId());
    }

    private TimelineEvent.InventoryUpdate getInventorySnapshotForPlayer(UUID uuid) {
        String uuidStr = uuid.toString();
        for (TimelineEvent event : timeline) {
            if (event instanceof TimelineEvent.InventoryUpdate inv
                && uuidStr.equals(inv.uuid())) {
                return inv;
            }
        }
        return null;
    }

    private int getRecordedTickAtIndex(int index) {
        if (timeline == null || timeline.isEmpty()) return 0;
        int safeIndex = Math.clamp(index, 0, timeline.size() - 1);
        return timeline.get(safeIndex).tick();
    }

    private Location locationFromEvent(TimelineEvent event) {
        return switch (event) {
            case TimelineEvent.PlayerMove e -> {
                World w = Bukkit.getWorld(e.world());
                yield w != null ? new Location(w, e.x(), e.y(), e.z(), e.yaw(), e.pitch()) : null;
            }
            case TimelineEvent.EntityMove e -> {
                World w = Bukkit.getWorld(e.world());
                yield w != null ? new Location(w, e.x(), e.y(), e.z(), e.yaw(), e.pitch()) : null;
            }
            case TimelineEvent.EntitySpawn e -> {
                World w = Bukkit.getWorld(e.world());
                yield w != null ? new Location(w, e.x(), e.y(), e.z(), 0f, 0f) : null;
            }
            default -> null;
        };
    }

    private int findTimelineIndexAfterRecordedTick(int targetRecordedTick) {
        int low = 0;
        int high = timeline.size() - 1;
        int result = timeline.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midTick = getRecordedTickAtIndex(mid);
            if (midTick > targetRecordedTick) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return Math.clamp(result, 0, timeline.size());
    }

    private boolean isActive() {
        return replayRegistry.contains(this);
    }

    private String formatTime(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void sendActionBar() {
        int currentRecordedTick = tick > 0 ? getRecordedTickAtIndex(tick - 1) : 0;
        int totalRecordedTicks = getRecordedTickAtIndex(timeline.size() - 1);
        String current = formatTime(currentRecordedTick);
        String total = formatTime(totalRecordedTicks);
        int percent = totalRecordedTicks > 0 ? (currentRecordedTick * 100 / totalRecordedTicks) : 0;

        Component bar;
        if (paused) {
            bar = Component.text("\u23F8 Replay paused: ", NamedTextColor.YELLOW)
                .append(Component.text(current + " / " + total, NamedTextColor.GRAY));
        } else {
            bar = Component.text("\u25B6 Replay: ", NamedTextColor.GREEN)
                .append(Component.text(current + " / " + total, NamedTextColor.GRAY))
                .append(Component.text(" (" + percent + "%)", NamedTextColor.DARK_GRAY));
        }
        viewer.sendActionBar(bar);
    }

    public Player getViewer() {
        return viewer;
    }

    public ReplayInventoryUI getInventoryUI() {
        return inventoryUI;
    }

    public RecordedEntity getRecordedEntity(int entityId) {
        for (RecordedEntity e : recordedEntities.values()) {
            if (e.getFakeEntityId() == entityId)
                return e;
        }
        return null;
    }
}
