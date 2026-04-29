package me.justindevb.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import java.util.List;
import java.util.UUID;
import me.justindevb.replay.recording.TimelineEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplaySessionBoundaryTest {

    @Mock private Replay replay;
    @Mock private PluginManager pluginManager;
    @Mock private Player viewer;
    @Mock private PlayerInventory viewerInventory;

    private ReplaySession createSession(ReplayRegistry registry) {
        List<TimelineEvent> timeline = List.of(
                new TimelineEvent.PlayerMove(0, UUID.randomUUID().toString(), "P", "world", 0, 64, 0, 0f, 0f, null)
        );
        return new ReplaySession(timeline, null, viewer, replay, registry);
    }

    @Test
    void stop_doubleCall_isIdempotent() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PacketEvents> packetEvents = mockStatic(PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            packetEvents.when(PacketEvents::getAPI).thenReturn(mock(PacketEventsAPI.class, Mockito.RETURNS_DEEP_STUBS));
            // stop() goes through ReplayInventoryUI.restoreInventory(), which calls viewer.getInventory().
            when(viewer.getInventory()).thenReturn(viewerInventory);

            ReplayRegistry registry = new ReplayRegistry();
            ReplaySession session = createSession(registry);
            registry.add(session);

            session.stop();
            session.stop();

            verify(pluginManager, times(1)).callEvent(any());
            verify(viewer, times(1)).sendMessage("Replay finished");
            assertFalse(registry.contains(session));
        }
    }
}
