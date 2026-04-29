package me.justindevb.replay.playback;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import me.justindevb.replay.Replay;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.entity.RecordedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayInteractionHandlerTest {

    @Mock private Player viewer;
    @Mock private Replay replay;
    @Mock private FakeEntityManager fakeEntityManager;
    @Mock private PluginManager pluginManager;

    private final Map<UUID, RecordedEntity> recordedEntities = new HashMap<>();
    private final Supplier<Map<UUID, RecordedEntity>> recordedEntitiesSupplier = () -> recordedEntities;

    /**
     * Builds a handler under a mocked Bukkit + PacketEvents environment so the
     * constructor's listener registration does not blow up on the static singletons.
     */
    private ReplayInteractionHandler createHandler() {
        return new ReplayInteractionHandler(viewer, replay, recordedEntitiesSupplier, fakeEntityManager);
    }

    @Test
    void onEntityInteract_byOtherPlayer_isIgnored() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<com.github.retrooper.packetevents.PacketEvents> packetEvents = mockStatic(com.github.retrooper.packetevents.PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(com.github.retrooper.packetevents.PacketEvents::getAPI).thenReturn(mock(com.github.retrooper.packetevents.PacketEventsAPI.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

            ReplayInteractionHandler handler = createHandler();

            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
            when(event.getPlayer()).thenReturn(mock(Player.class));

            handler.onEntityInteract(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Test
    void onEntityInteract_nonPlayerTarget_isIgnored() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<com.github.retrooper.packetevents.PacketEvents> packetEvents = mockStatic(com.github.retrooper.packetevents.PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(com.github.retrooper.packetevents.PacketEvents::getAPI).thenReturn(mock(com.github.retrooper.packetevents.PacketEventsAPI.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

            ReplayInteractionHandler handler = createHandler();

            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
            when(event.getPlayer()).thenReturn(viewer);
            when(event.getRightClicked()).thenReturn(mock(Entity.class));

            handler.onEntityInteract(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Test
    void onEntityInteract_viewerRecordedPlayer_opensInventoryAndCancels() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<com.github.retrooper.packetevents.PacketEvents> packetEvents = mockStatic(com.github.retrooper.packetevents.PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(com.github.retrooper.packetevents.PacketEvents::getAPI).thenReturn(mock(com.github.retrooper.packetevents.PacketEventsAPI.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

            ReplayInteractionHandler handler = createHandler();

            Player fake = mock(Player.class);
            UUID fakeUuid = UUID.randomUUID();
            when(fake.getUniqueId()).thenReturn(fakeUuid);

            RecordedPlayer rp = mock(RecordedPlayer.class);
            recordedEntities.put(fakeUuid, rp);

            PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
            when(event.getPlayer()).thenReturn(viewer);
            when(event.getRightClicked()).thenReturn(fake);

            handler.onEntityInteract(event);

            verify(rp).openInventoryForViewer(viewer);
            verify(event).setCancelled(true);
        }
    }

    @Test
    void onPacketReceive_interactFromOtherViewer_isIgnored() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<com.github.retrooper.packetevents.PacketEvents> packetEvents = mockStatic(com.github.retrooper.packetevents.PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(com.github.retrooper.packetevents.PacketEvents::getAPI).thenReturn(mock(com.github.retrooper.packetevents.PacketEventsAPI.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

            ReplayInteractionHandler handler = createHandler();

            PacketReceiveEvent event = mock(PacketReceiveEvent.class);
            when(event.getPacketType()).thenReturn(PacketType.Play.Client.INTERACT_ENTITY);
            when(event.getPlayer()).thenReturn(mock(Player.class));

            handler.onPacketReceive(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Test
    void onPacketReceive_nonInteractPacket_isIgnored() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<com.github.retrooper.packetevents.PacketEvents> packetEvents = mockStatic(com.github.retrooper.packetevents.PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(com.github.retrooper.packetevents.PacketEvents::getAPI).thenReturn(mock(com.github.retrooper.packetevents.PacketEventsAPI.class, org.mockito.Mockito.RETURNS_DEEP_STUBS));

            ReplayInteractionHandler handler = createHandler();

            PacketReceiveEvent event = mock(PacketReceiveEvent.class);
            when(event.getPacketType()).thenReturn(PacketType.Play.Client.PLAYER_POSITION);

            handler.onPacketReceive(event);

            verify(event, never()).setCancelled(true);
        }
    }
}
