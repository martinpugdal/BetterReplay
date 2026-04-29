package me.justindevb.replay.playback;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.recording.TimelineEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks transient client-side entities created during replay playback (fake mobs,
 * falling blocks, item drops) and owns the packet sends that spawn and destroy them.
 * The viewer is the only player ever shown these entities — they exist purely on
 * the client side and never enter the server world.
 */
public class FakeEntityManager {

    private final Player viewer;
    private final Set<Integer> trackedIds = new HashSet<>();

    public FakeEntityManager(Player viewer) {
        this.viewer = viewer;
    }

    public void track(int entityId) {
        trackedIds.add(entityId);
    }

    public void untrack(int entityId) {
        trackedIds.remove(entityId);
    }

    public boolean isTracked(int entityId) {
        return trackedIds.contains(entityId);
    }

    public void clearAll() {
        for (int id : trackedIds) {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(id);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
        }
        trackedIds.clear();
    }

    public void spawnFakeMob(RecordedEntity entity, TimelineEvent.EntitySpawn event) {
        Location loc = new Location(Bukkit.getWorld(event.world()),
            event.x(), event.y(), event.z(), 0f, 0f);

        entity.spawn(loc);

        trackedIds.add(entity.getFakeEntityId());

        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(
            entity.getFakeEntityId(),
            Collections.emptyList()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, meta);
    }

    public void spawnFakeFallingBlock(RecordedEntity entity, TimelineEvent.EntitySpawn event) {
        Location loc = new Location(Bukkit.getWorld(event.world()),
            event.x(), event.y(), event.z(), 0f, 0f);

        org.bukkit.block.data.BlockData bukkitData;
        try {
            bukkitData = Bukkit.createBlockData(event.blockData());
        } catch (IllegalArgumentException ex) {
            spawnFakeMob(entity, event);
            return;
        }
        int blockStateId = SpigotConversionUtil.fromBukkitBlockData(bukkitData).getGlobalId();

        int entityId = entity.getFakeEntityId();
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
            entityId,
            entity.getUuid(),
            EntityTypes.FALLING_BLOCK,
            SpigotConversionUtil.fromBukkitLocation(loc),
            0f,
            blockStateId,
            new Vector3d(0, 0, 0)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);

        trackedIds.add(entityId);
    }

    public void spawnFakeDroppedItem(ItemStack stack, Location loc, Vector3d velocity) {
        int entityId = SpigotReflectionUtil.generateEntityId();
        trackedIds.add(entityId);

        com.github.retrooper.packetevents.protocol.item.ItemStack nmsStack = SpigotConversionUtil.fromBukkitItemStack(stack);

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
            entityId,
            UUID.randomUUID(),
            EntityTypes.ITEM,
            SpigotConversionUtil.fromBukkitLocation(loc),
            loc.getYaw(),
            0,
            velocity
        );

        EntityData<com.github.retrooper.packetevents.protocol.item.ItemStack> itemData = new EntityData<>(8, EntityDataTypes.ITEMSTACK, nmsStack);
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(
            entityId,
            Collections.singletonList(itemData)
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, meta);
    }
}
