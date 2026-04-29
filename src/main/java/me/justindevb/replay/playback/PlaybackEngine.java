package me.justindevb.replay.playback;

import com.github.retrooper.packetevents.util.Vector3d;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.entity.RecordedPlayer;
import me.justindevb.replay.recording.TimelineEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;

import static me.justindevb.replay.util.io.ItemStackSerializer.deserializeItem;

/**
 * Dispatches replay timeline events to the appropriate RecordedEntity methods.
 * Spawning of fake mobs, falling blocks, and dropped items is delegated to
 * {@link FakeEntityManager}.
 */
public class PlaybackEngine {

    private final Set<UUID> deadEntities;
    private final Map<UUID, RecordedEntity> recordedEntities;
    private final ReplayBlockManager blockManager;
    private final FakeEntityManager fakeEntityManager;

    public PlaybackEngine(Set<UUID> deadEntities,
                          Map<UUID, RecordedEntity> recordedEntities,
                          ReplayBlockManager blockManager,
                          FakeEntityManager fakeEntityManager) {
        this.deadEntities = deadEntities;
        this.recordedEntities = recordedEntities;
        this.blockManager = blockManager;
        this.fakeEntityManager = fakeEntityManager;
    }

    public void handleEvent(RecordedEntity entity, TimelineEvent event) {
        switch (event) {
            case TimelineEvent.PlayerMove e -> {
                World world = Bukkit.getWorld(e.world());
                if (world == null) return;
                Location loc = new Location(world, e.x(), e.y(), e.z(), e.yaw(), e.pitch());
                entity.moveTo(loc);
                if (e.pose() != null && entity instanceof RecordedPlayer rp) {
                    try {
                        rp.setPose(Pose.valueOf(e.pose()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            case TimelineEvent.EntityMove e -> {
                World world = Bukkit.getWorld(e.world());
                if (world == null) return;
                Location loc = new Location(world, e.x(), e.y(), e.z(), e.yaw(), e.pitch());
                entity.moveTo(loc);
            }
            case TimelineEvent.SneakToggle e -> {
                if (entity instanceof RecordedPlayer rp) rp.updateSneak(e.sneaking());
            }
            case TimelineEvent.Attack e -> {
                if (entity instanceof RecordedPlayer rp) rp.playAttackAnimation();
            }
            case TimelineEvent.BlockPlace e -> {
                if (entity instanceof RecordedPlayer rp) rp.showBlockPlace();
                blockManager.applyReplayBlockChange(e, false);
            }
            case TimelineEvent.BlockBreak e -> {
                if (entity instanceof RecordedPlayer rp) rp.showBlockBreak(e.x(), e.y(), e.z(), 9);
                blockManager.applyReplayBlockChange(e, false);
            }
            case TimelineEvent.BlockBreakStage e -> {
                if (entity instanceof RecordedPlayer rp) rp.showBlockBreak(e.x(), e.y(), e.z(), e.stage());
            }
            case TimelineEvent.Swing e -> {
                if (entity instanceof RecordedPlayer rp) rp.playSwing(e.hand());
            }
            case TimelineEvent.Damaged e -> entity.showDamage();
            case TimelineEvent.SprintToggle e -> {
                if (entity instanceof RecordedPlayer rp) rp.updateSprint(e.sprinting());
            }
            case TimelineEvent.EntityDeath e -> {
                entity.showDeath();
                deadEntities.add(entity.getUuid());
                entity.destroy();
                recordedEntities.remove(entity.getUuid());
            }
            case TimelineEvent.InventoryUpdate e -> {
                if (entity instanceof RecordedPlayer rp) rp.updateInventory(e);
            }
            case TimelineEvent.HeldItemChange e -> {
                if (entity instanceof RecordedPlayer rp) rp.updateHeldItems(e);
            }
            case TimelineEvent.ItemDrop e -> {
                ItemStack stack = deserializeItem(e.item());
                Location loc = (e.locWorld() != null)
                    ? new Location(Bukkit.getWorld(e.locWorld()), e.locX(), e.locY(), e.locZ(), e.locYaw(), e.locPitch())
                    : null;
                if (stack != null && loc != null) {
                    Vector3d vel = (e.vx() == 0 && e.vy() == 0 && e.vz() == 0)
                        ? null
                        : new Vector3d(e.vx(), e.vy(), e.vz());
                    fakeEntityManager.spawnFakeDroppedItem(stack, loc, vel);
                }
            }
            case TimelineEvent.EntitySpawn e -> {
                if ("FALLING_BLOCK".equals(e.etype()) && e.blockData() != null) {
                    fakeEntityManager.spawnFakeFallingBlock(entity, e);
                } else {
                    fakeEntityManager.spawnFakeMob(entity, e);
                }
            }
            case TimelineEvent.PlayerQuit e -> {
                UUID uuid = UUID.fromString(e.uuid());
                recordedEntities.remove(uuid);
                if (entity == null) return;
                entity.destroy();
                fakeEntityManager.untrack(entity.getFakeEntityId());
            }
            default -> {
            } // BlockBreakComplete, etc. — no playback action needed
        }
    }
}
