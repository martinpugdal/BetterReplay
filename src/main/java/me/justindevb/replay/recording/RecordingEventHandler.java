package me.justindevb.replay.recording;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;

import static me.justindevb.replay.util.io.ItemStackSerializer.serializeItem;

/**
 * Handles Bukkit events during a recording session.
 * Captures block breaks/places, deaths, attacks, animations, and entity spawns
 * into the timeline via the TimelineBuilder.
 */
public class RecordingEventHandler implements Listener {

    private final EntityTracker tracker;
    private final TimelineBuilder builder;
    private final TickProvider tickProvider;
    private final Consumer<UUID> onPlayerRemoved;
    private final Predicate<Location> snapshotBounds;

    public RecordingEventHandler(EntityTracker tracker, TimelineBuilder builder, TickProvider tickProvider) {
        this(tracker, builder, tickProvider, uuid -> {
        }, null);
    }

    public RecordingEventHandler(EntityTracker tracker, TimelineBuilder builder, TickProvider tickProvider, Consumer<UUID> onPlayerRemoved) {
        this(tracker, builder, tickProvider, onPlayerRemoved, null);
    }

    /**
     * @param snapshotBounds tests whether a {@link Location} falls inside the
     *                       snapshotted region. When non-null this enables
     *                       capture of non-player block changes (explosions,
     *                       water/lava flow, decay, etc.) inside that region
     *                       so the replay reflects them faithfully.
     */
    public RecordingEventHandler(EntityTracker tracker, TimelineBuilder builder, TickProvider tickProvider,
                                 Consumer<UUID> onPlayerRemoved, Predicate<Location> snapshotBounds) {
        this.tracker = tracker;
        this.builder = builder;
        this.tickProvider = tickProvider;
        this.onPlayerRemoved = onPlayerRemoved;
        this.snapshotBounds = snapshotBounds;
    }

    private boolean isInBounds(Location loc) {
        return snapshotBounds != null && loc != null && snapshotBounds.test(loc);
    }

    private void recordSystemBreak(Block block) {
        builder.addEvent(new TimelineEvent.BlockBreak(
            tickProvider.getTick(),
            TimelineEvent.SYSTEM_ACTOR,
            block.getWorld().getName(),
            block.getX(), block.getY(), block.getZ(),
            block.getBlockData().getAsString()
        ));
    }

    private void recordSystemPlace(Block block, String placedBlockData, String replacedBlockData) {
        builder.addEvent(new TimelineEvent.BlockPlace(
            tickProvider.getTick(),
            TimelineEvent.SYSTEM_ACTOR,
            block.getWorld().getName(),
            block.getX(), block.getY(), block.getZ(),
            placedBlockData,
            replacedBlockData
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.BlockBreak(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.getBlock().getWorld().getName(),
            e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(),
            e.getBlock().getBlockData().getAsString()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.BlockBreakComplete(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.getBlock().getWorld().getName(),
            e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!tracker.isTrackedPlayer(p.getUniqueId())) return;

        ItemStack dropped = e.getItemDrop().getItemStack();
        Location loc = e.getItemDrop().getLocation();
        org.bukkit.util.Vector vel = e.getItemDrop().getVelocity();

        builder.addEvent(new TimelineEvent.ItemDrop(
            tickProvider.getTick(),
            p.getUniqueId().toString(),
            serializeItem(dropped),
            loc.getWorld().getName(),
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getYaw(), loc.getPitch(),
            vel.getX(), vel.getY(), vel.getZ()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.BlockPlace(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.getBlock().getWorld().getName(),
            e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(),
            e.getBlock().getBlockData().getAsString(),
            e.getBlockReplacedState().getBlockData().getAsString()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!tracker.isTrackedPlayer(p.getUniqueId())) return;

        Entity entity = e.getEntity();

        String targetUuid = (entity instanceof Player target) ? target.getUniqueId().toString() : null;

        builder.addEvent(new TimelineEvent.Attack(
            tickProvider.getTick(),
            p.getUniqueId().toString(),
            targetUuid,
            entity.getUniqueId().toString(),
            entity.getType().name()
        ));

        if (!(entity instanceof Player) && !tracker.isEntityTracked(entity.getUniqueId())) {
            tracker.trackEntity(entity.getUniqueId(), entity.getType());

            String spawnBlockData = (entity instanceof org.bukkit.entity.FallingBlock fb)
                ? fb.getBlockData().getAsString()
                : null;

            builder.addEvent(new TimelineEvent.EntitySpawn(
                tickProvider.getTick(),
                entity.getUniqueId().toString(),
                entity.getType().name(),
                entity.getWorld().getName(),
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ(),
                spawnBlockData
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.Swing(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.getAnimationType().name()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprintToggle(PlayerToggleSprintEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.SprintToggle(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.isSprinting()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!tracker.isTrackedPlayer(e.getPlayer().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.SneakToggle(
            tickProvider.getTick(),
            e.getPlayer().getUniqueId().toString(),
            e.isSneaking()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!tracker.isTrackedPlayer(p.getUniqueId())) return;

        builder.addEvent(new TimelineEvent.HeldItemChange(
            tickProvider.getTick(),
            p.getUniqueId().toString(),
            serializeItem(e.getOffHandItem()),
            serializeItem(e.getMainHandItem())
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!tracker.isTrackedPlayer(p.getUniqueId())) return;

        builder.addEvent(new TimelineEvent.HeldItemChange(
            tickProvider.getTick(),
            p.getUniqueId().toString(),
            serializeItem(p.getInventory().getItem(e.getNewSlot())),
            serializeItem(p.getInventory().getItemInOffHand())
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamaged(EntityDamageEvent e) {
        if (!tracker.isTrackedPlayer(e.getEntity().getUniqueId())) return;

        builder.addEvent(new TimelineEvent.Damaged(
            tickProvider.getTick(),
            e.getEntity().getUniqueId().toString(),
            e.getEntity().getType().name(),
            e.getCause().name(),
            e.getFinalDamage()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!tracker.isNearbyTrackedPlayer(e.getEntity().getLocation())) return;

        UUID uuid = e.getEntity().getUniqueId();
        if (tracker.isEntityTracked(uuid)) return;

        tracker.trackEntity(uuid, e.getEntityType());

        String blockData = (e.getEntity() instanceof org.bukkit.entity.FallingBlock fb)
            ? fb.getBlockData().getAsString()
            : null;

        builder.addEvent(new TimelineEvent.EntitySpawn(
            tickProvider.getTick(),
            uuid.toString(),
            e.getEntityType().name(),
            e.getLocation().getWorld().getName(),
            e.getEntity().getLocation().getX(),
            e.getEntity().getLocation().getY(),
            e.getEntity().getLocation().getZ(),
            blockData
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e) {
        Entity entity = e.getEntity();

        UUID uuid = entity.getUniqueId();
        if (!tracker.isEntityTracked(uuid)) return;

        builder.addEvent(new TimelineEvent.EntityDeath(
            tickProvider.getTick(),
            uuid.toString(),
            e.getEntityType().name(),
            entity.getLocation().getWorld().getName(),
            entity.getLocation().getX(),
            entity.getLocation().getY(),
            entity.getLocation().getZ()
        ));

        if (!(entity instanceof Player))
            tracker.removeEntity(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        Player p = e.getPlayer();

        if (!tracker.isTrackedPlayer(uuid)) return;

        builder.addEvent(new TimelineEvent.EntityDeath(
            tickProvider.getTick(),
            uuid.toString(),
            e.getEntityType().name(),
            p.getWorld().getName(),
            p.getLocation().getX(),
            p.getLocation().getY(),
            p.getLocation().getZ()
        ));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        if (tracker.isTrackedPlayer(p.getUniqueId())) {
            builder.addEvent(new TimelineEvent.PlayerQuit(
                tickProvider.getTick(),
                p.getUniqueId().toString()
            ));
            tracker.removePlayer(p.getUniqueId());
            onPlayerRemoved.accept(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (snapshotBounds == null) return;
        for (Block block : e.blockList()) {
            if (!isInBounds(block.getLocation())) continue;
            recordSystemBreak(block);
        }
    }

    // -- Non-player block changes inside the snapshotted region --
    // Captured so that environment changes during recording (explosions, water flow,
    // decay, mob-driven block changes, etc.) are reflected in the replay even when
    // they did not originate from a tracked player.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (snapshotBounds == null) return;
        for (Block block : e.blockList()) {
            if (!isInBounds(block.getLocation())) continue;
            recordSystemBreak(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (snapshotBounds == null) return;
        Block toBlock = e.getToBlock();
        if (!isInBounds(toBlock.getLocation())) return;
        // Liquid (water/lava) flows into toBlock; capture the source liquid as the new state.
        String placed = e.getBlock().getBlockData().getAsString();
        String replaced = toBlock.getBlockData().getAsString();
        recordSystemPlace(toBlock, placed, replaced);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        if (snapshotBounds == null) return;
        Block block = e.getBlock();
        if (!isInBounds(block.getLocation())) return;
        BlockState newState = e.getNewState();
        String placed = newState.getBlockData().getAsString();
        String replaced = block.getBlockData().getAsString();
        recordSystemPlace(block, placed, replaced);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent e) {
        if (snapshotBounds == null) return;
        Block block = e.getBlock();
        if (!isInBounds(block.getLocation())) return;
        BlockState newState = e.getNewState();
        String placed = newState.getBlockData().getAsString();
        String replaced = block.getBlockData().getAsString();
        recordSystemPlace(block, placed, replaced);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        if (snapshotBounds == null) return;
        Block block = e.getBlock();
        if (!isInBounds(block.getLocation())) return;
        recordSystemBreak(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.FallingBlock) return;
        if (snapshotBounds == null) return;
        Block block = e.getBlock();
        if (!isInBounds(block.getLocation())) return;
        String placed = e.getBlockData() != null
            ? e.getBlockData().getAsString()
            : e.getTo().createBlockData().getAsString();
        String replaced = block.getBlockData().getAsString();
        recordSystemPlace(block, placed, replaced);
    }

    // FallingBlock entity ↔ block transitions. Runs unconditionally (independent of
    // snapshotBounds) so the source ghost block is removed when a FallingBlock spawns,
    // and the fake entity is destroyed + a real block placed when it lands. Without
    // this, viewers see a floating source block and/or a stuck FallingBlock entity
    // after landing because Bukkit's EntityDeathEvent never fires for non-LivingEntity.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.FallingBlock fb)) return;
        UUID uuid = fb.getUniqueId();
        if (!tracker.isEntityTracked(uuid)) return;

        Block block = e.getBlock();
        int tick = tickProvider.getTick();

        if (e.getTo() == org.bukkit.Material.AIR) {
            recordSystemBreak(block);
        } else {
            builder.addEvent(new TimelineEvent.EntityDeath(
                tick,
                uuid.toString(),
                fb.getType().name(),
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()
            ));
            tracker.removeEntity(uuid);

            String placed = e.getBlockData() != null
                ? e.getBlockData().getAsString()
                : e.getTo().createBlockData().getAsString();
            String replaced = block.getBlockData().getAsString();
            recordSystemPlace(block, placed, replaced);
        }
    }

    @FunctionalInterface
    public interface TickProvider {
        int getTick();
    }
}
