package me.justindevb.replay.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.justindevb.replay.Replay;
import me.justindevb.replay.ReplayRegistry;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.entity.RecordedPlayer;
import org.bukkit.entity.Player;

public class PacketEventsListener implements PacketListener {
    private final Replay replay;
    private final ReplayRegistry replayRegistry;

    public PacketEventsListener(Replay replay, ReplayRegistry replayRegistry) {
        this.replay = replay;
        this.replayRegistry = replayRegistry;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {

        if (!event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY))
            return;

        Player viewer = event.getPlayer();
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        int entityId = wrapper.getEntityId();
        RecordedEntity clicked = replayRegistry.getEntityById(entityId);

        if (clicked == null) {
            return;
        }

        if (clicked instanceof RecordedPlayer rp) {
            replay.getFoliaLib().getScheduler().runNextTick(task -> {
                rp.openInventoryForViewer(viewer);
            });
            event.setCancelled(true);
        }
    }
}
