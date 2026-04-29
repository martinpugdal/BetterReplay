package me.justindevb.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import me.justindevb.replay.recording.TimelineEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplaySessionIntegrationTest {

    @Mock private Replay replay;
    @Mock private Player viewer;
    @Mock private PlayerInventory inventory;
    @Mock private PluginManager pluginManager;
    @Mock private FoliaLib foliaLib;
    @Mock private PlatformScheduler scheduler;
    @Mock private BlockData blockData;

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PacketEvents> packetEvents;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(blockData);
        bukkit.when(() -> Bukkit.createBlockData(anyString())).thenReturn(blockData);
        when(blockData.getAsString()).thenReturn("minecraft:air");

        // ReplayInteractionHandler constructor registers a PacketEvents listener;
        // mock the static singleton so unit tests do not need a live PacketEvents instance.
        packetEvents = mockStatic(PacketEvents.class);
        packetEvents.when(PacketEvents::getAPI).thenReturn(mock(PacketEventsAPI.class, Mockito.RETURNS_DEEP_STUBS));

        when(replay.getFoliaLib()).thenReturn(foliaLib);
        when(foliaLib.getScheduler()).thenReturn(scheduler);

        when(viewer.getInventory()).thenReturn(inventory);
        when(viewer.isOnline()).thenReturn(true);

        // Avoid item interactions when stop() restores inventory.
        doNothing().when(inventory).clear();
        doNothing().when(inventory).setContents(any());
        doNothing().when(inventory).setArmorContents(any());
        doNothing().when(inventory).setItemInOffHand(any());
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        packetEvents.close();
    }

    private ReplaySession createSession(ReplayRegistry registry) {
        List<TimelineEvent> timeline = List.of(
                new TimelineEvent.BlockPlace(0, null, null, 0, 64, 0, "minecraft:stone", null),
                new TimelineEvent.BlockBreak(20, null, null, 0, 64, 0, "minecraft:stone"),
                new TimelineEvent.BlockPlace(40, null, null, 0, 64, 0, "minecraft:dirt", null)
        );
        return new ReplaySession(timeline, null, viewer, replay, registry);
    }

    /**
     * Builds a session whose timeline is a series of {@link TimelineEvent.PlayerQuit} events
     * with random UUIDs at distinct ticks. PlayerQuit events with no matching {@code recordedEntities}
     * entry advance the tick loop without spawning fake entities or touching Bukkit's
     * Material/ItemFactory machinery, which keeps the scheduler-callback harness tests
     * deterministic and free of full-server bootstrap.
     */
    private ReplaySession createPlayerQuitSession(ReplayRegistry registry, int eventCount) {
        TimelineEvent[] events = new TimelineEvent[eventCount];
        for (int i = 0; i < eventCount; i++) {
            events[i] = new TimelineEvent.PlayerQuit(i, UUID.randomUUID().toString());
        }
        return new ReplaySession(List.of(events), null, viewer, replay, registry);
    }

    private static void invokeSkipSeconds(ReplaySession session, int seconds) throws Exception {
        Method m = ReplaySession.class.getDeclaredMethod("skipSeconds", int.class);
        m.setAccessible(true);
        m.invoke(session, seconds);
    }

    private static int getTick(ReplaySession session) throws Exception {
        Field f = ReplaySession.class.getDeclaredField("tick");
        f.setAccessible(true);
        return (int) f.get(session);
    }

    private static void setTick(ReplaySession session, int value) throws Exception {
        Field f = ReplaySession.class.getDeclaredField("tick");
        f.setAccessible(true);
        f.set(session, value);
    }

    private static void setPaused(ReplaySession session, boolean paused) throws Exception {
        Field f = ReplaySession.class.getDeclaredField("paused");
        f.setAccessible(true);
        f.set(session, paused);
    }

    private static void setReplayTask(ReplaySession session, WrappedTask task) throws Exception {
        Field f = ReplaySession.class.getDeclaredField("replayTask");
        f.setAccessible(true);
        f.set(session, task);
    }

    private static WrappedTask getReplayTask(ReplaySession session) throws Exception {
        Field f = ReplaySession.class.getDeclaredField("replayTask");
        f.setAccessible(true);
        return (WrappedTask) f.get(session);
    }

    @Test
    void skipSeconds_seekTransitionsStayWithinTimelineBounds() throws Exception {
        ReplaySession session = createSession(new ReplayRegistry());

        invokeSkipSeconds(session, 999);
        int afterForward = getTick(session);
        assertTrue(afterForward >= 0 && afterForward <= 3);

        invokeSkipSeconds(session, -999);
        int afterBackward = getTick(session);
        assertTrue(afterBackward >= 0 && afterBackward <= 3);
    }

    @Test
    void stop_withScheduledReplayTask_cancelsTaskAndUnregistersSession() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createSession(registry);
        WrappedTask task = mock(WrappedTask.class);

        registry.add(session);
        setReplayTask(session, task);

        session.stop();

        verify(scheduler).cancelTask(task);
        assertFalse(registry.contains(session));
    }

    @Test
    void stop_withoutReplayTask_stillCleansUp() {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createSession(registry);

        registry.add(session);
        session.stop();

        verify(scheduler, never()).cancelTask(any(WrappedTask.class));
        assertFalse(registry.contains(session));
    }

    // -- Scheduler-callback harness coverage --

    @Test
    void runTickStep_advancesTickAcrossDistinctRecordedTicks() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createPlayerQuitSession(registry, 3);
        registry.add(session);
        WrappedTask task = mock(WrappedTask.class);

        // Each call should drain all events at the current recordedTick. Since each
        // PlayerQuit lives at a unique tick, the index advances by exactly one per call.
        invokeRunTickStep(session, task);
        assertEquals(1, getTick(session));

        invokeRunTickStep(session, task);
        assertEquals(2, getTick(session));

        invokeRunTickStep(session, task);
        assertEquals(3, getTick(session));

        // The session captured the live task reference each time the loop ran.
        assertSame(task, getReplayTask(session));
        verify(task, never()).cancel();
    }

    @Test
    void runTickStep_endOfTimeline_cancelsTaskAndStopsSession() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createPlayerQuitSession(registry, 1);
        registry.add(session);
        WrappedTask task = mock(WrappedTask.class);

        // Force "already past the end" state: tick == timeline.size().
        setTick(session, 1);

        invokeRunTickStep(session, task);

        // Direct cancel from runTickStep, plus stop()'s scheduler-side cancel.
        verify(task).cancel();
        verify(scheduler).cancelTask(task);
        assertFalse(registry.contains(session));
        verify(viewer).sendMessage("Replay finished");
    }

    @Test
    void runTickStep_viewerOffline_cancelsTaskWithoutStopping() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createPlayerQuitSession(registry, 2);
        registry.add(session);
        WrappedTask task = mock(WrappedTask.class);

        when(viewer.isOnline()).thenReturn(false);

        invokeRunTickStep(session, task);

        verify(task).cancel();
        // Session is NOT stopped via stop() on the offline path -- registry is untouched.
        assertTrue(registry.contains(session));
        verify(scheduler, never()).cancelTask(any(WrappedTask.class));
        verify(viewer, never()).sendMessage("Replay finished");
        // Tick did not advance because the offline branch returns immediately.
        assertEquals(0, getTick(session));
    }

    // -- Pause/resume scheduler-flow coverage --

    @Test
    void runTickStep_paused_doesNotAdvanceTickAndPreservesReplayTask() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createPlayerQuitSession(registry, 3);
        registry.add(session);
        WrappedTask task = mock(WrappedTask.class);

        setPaused(session, true);

        invokeRunTickStep(session, task);
        invokeRunTickStep(session, task);

        assertEquals(0, getTick(session));
        // The paused branch returns BEFORE replayTask is assigned, so the session
        // never holds a reference to the (still-running) task while paused.
        assertNull(getReplayTask(session));
        verify(task, never()).cancel();
        // Action bar still updates during pause so the viewer sees the paused indicator.
        verify(viewer, atLeastOnce()).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void runTickStep_pausedThenResumed_advancesAfterResume() throws Exception {
        ReplayRegistry registry = new ReplayRegistry();
        ReplaySession session = createPlayerQuitSession(registry, 2);
        registry.add(session);
        WrappedTask task = mock(WrappedTask.class);

        setPaused(session, true);
        invokeRunTickStep(session, task);
        assertEquals(0, getTick(session), "tick must not advance while paused");

        setPaused(session, false);
        invokeRunTickStep(session, task);
        assertEquals(1, getTick(session), "tick must advance once resumed");

        // After resume the loop wired the scheduled task into the session reference.
        assertSame(task, getReplayTask(session));
    }

    private static void invokeRunTickStep(ReplaySession session, WrappedTask task) throws Exception {
        // runTickStep is package-private; same-package invocation works without reflection,
        // but we go through reflection so this helper stays robust if visibility changes.
        Method m = ReplaySession.class.getDeclaredMethod("runTickStep", WrappedTask.class);
        m.setAccessible(true);
        m.invoke(session, task);
    }
}
