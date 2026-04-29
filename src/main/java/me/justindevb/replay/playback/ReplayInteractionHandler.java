package me.justindevb.replay.playback;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import me.justindevb.replay.Replay;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.entity.RecordedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/**
 * Handles viewer interactions with replayed entities: right-click on a replayed player
 * opens that player's recorded inventory, and clicks on transient fake entities
 * (item drops, falling blocks) are silently swallowed so the viewer cannot pick them up.
 *
 * <p>Listens both via Bukkit's {@link PlayerInteractAtEntityEvent} (server-side, fires
 * for entities the viewer can see in the world) and via PacketEvents' {@code INTERACT_ENTITY}
 * client packet (catches clicks on client-only fake entities that have no server presence).</p>
 */
public class ReplayInteractionHandler implements Listener, PacketListener {

    private final Player viewer;
    private final Supplier<Map<UUID, RecordedEntity>> recordedEntitiesSupplier;
    private final FakeEntityManager fakeEntityManager;
    private PacketListenerCommon packetListenerHandle;

    public ReplayInteractionHandler(Player viewer,
                                    Replay replay,
                                    Supplier<Map<UUID, RecordedEntity>> recordedEntitiesSupplier,
                                    FakeEntityManager fakeEntityManager) {
        this.viewer = viewer;
        this.recordedEntitiesSupplier = recordedEntitiesSupplier;
        this.fakeEntityManager = fakeEntityManager;

        Bukkit.getPluginManager().registerEvents(this, replay);
        this.packetListenerHandle = PacketEvents.getAPI().getEventManager()
            .registerListener(this, PacketListenerPriority.NORMAL);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (packetListenerHandle != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListenerHandle);
            packetListenerHandle = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityInteract(PlayerInteractAtEntityEvent e) {
        if (!viewer.equals(e.getPlayer()))
            return;
        if (!(e.getRightClicked() instanceof Player fake))
            return;
        RecordedEntity recordedEntity = recordedEntitiesSupplier.get().get(fake.getUniqueId());
        if (!(recordedEntity instanceof RecordedPlayer rp))
            return;
        rp.openInventoryForViewer(viewer);
        e.setCancelled(true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY))
            return;
        if (!event.getPlayer().equals(viewer))
            return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        int entityId = wrapper.getEntityId();
        if (fakeEntityManager.isTracked(entityId))
            event.setCancelled(true);
        RecordedEntity recordedEntity = findRecordedEntityByFakeId(entityId);
        if (recordedEntity instanceof RecordedPlayer rp) {
            rp.openInventoryForViewer(viewer);
            event.setCancelled(true);
        }
    }

    private RecordedEntity findRecordedEntityByFakeId(int entityId) {
        for (RecordedEntity e : recordedEntitiesSupplier.get().values()) {
            if (e.getFakeEntityId() == entityId)
                return e;
        }
        return null;
    }
}
