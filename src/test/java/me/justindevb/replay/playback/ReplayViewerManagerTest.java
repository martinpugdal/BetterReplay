package me.justindevb.replay.playback;

import me.justindevb.replay.Replay;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
class ReplayViewerManagerTest {

    @Mock private Player viewer;
    @Mock private Replay replay;
    @Mock private SessionControl sessionControl;
    @Mock private PluginManager pluginManager;

    private ReplayViewerManager createManager() {
        return new ReplayViewerManager(viewer, replay, sessionControl);
    }

    private ReplayViewerManager createManagerWithWorldChange(Runnable onWorldChanged) {
        return new ReplayViewerManager(viewer, replay, sessionControl, onWorldChanged);
    }

    @Test
    void captureAndRestore_roundTripsOriginalFlags() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            // Pre-replay state: viewer was vulnerable but able to pick up items.
            when(viewer.isInvulnerable()).thenReturn(false);
            when(viewer.getCanPickupItems()).thenReturn(true);

            ReplayViewerManager mgr = createManager();
            mgr.captureState();
            mgr.applyReplayState();
            mgr.restoreState();

            verify(viewer).setInvulnerable(true);
            verify(viewer).setCanPickupItems(false);
            verify(viewer).setInvulnerable(false);
            verify(viewer).setCanPickupItems(true);
        }
    }

    @Test
    void restoreState_withoutCapture_writesDefaults() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            ReplayViewerManager mgr = createManager();
            mgr.restoreState();

            verify(viewer).setInvulnerable(false);
            verify(viewer).setCanPickupItems(false);
        }
    }

    @Test
    void inheritStateFrom_copiesCapturedFlags() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            when(viewer.isInvulnerable()).thenReturn(false);
            when(viewer.getCanPickupItems()).thenReturn(true);

            ReplayViewerManager outer = createManager();
            outer.captureState();

            ReplayViewerManager inner = createManager();
            inner.inheritStateFrom(outer);
            inner.restoreState();

            verify(viewer).setInvulnerable(false);
            verify(viewer).setCanPickupItems(true);
        }
    }

    @Test
    void onPlayerQuit_viewerDisconnected_stopsSession() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            ReplayViewerManager mgr = createManager();
            PlayerQuitEvent event = mock(PlayerQuitEvent.class);
            when(event.getPlayer()).thenReturn(viewer);

            mgr.onPlayerQuit(event);

            verify(sessionControl).stop();
        }
    }

    @Test
    void onPlayerQuit_otherPlayerDisconnected_doesNotStop() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            ReplayViewerManager mgr = createManager();
            PlayerQuitEvent event = mock(PlayerQuitEvent.class);
            when(event.getPlayer()).thenReturn(mock(Player.class));

            mgr.onPlayerQuit(event);

            verify(sessionControl, never()).stop();
        }
    }

    @Test
    void onPlayerChangedWorld_viewer_runsCallback() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            when(sessionControl.isActive()).thenReturn(true);

            Runnable callback = mock(Runnable.class);
            ReplayViewerManager mgr = createManagerWithWorldChange(callback);
            PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
            when(event.getPlayer()).thenReturn(viewer);

            mgr.onPlayerChangedWorld(event);

            verify(callback).run();
        }
    }

    @Test
    void onPlayerChangedWorld_otherPlayer_doesNotRunCallback() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            Runnable callback = mock(Runnable.class);
            ReplayViewerManager mgr = createManagerWithWorldChange(callback);
            PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
            when(event.getPlayer()).thenReturn(mock(Player.class));

            mgr.onPlayerChangedWorld(event);

            verify(callback, never()).run();
        }
    }

    @Test
    void onPlayerChangedWorld_inactiveSession_doesNotRunCallback() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            when(sessionControl.isActive()).thenReturn(false);

            Runnable callback = mock(Runnable.class);
            ReplayViewerManager mgr = createManagerWithWorldChange(callback);
            PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
            when(event.getPlayer()).thenReturn(viewer);

            mgr.onPlayerChangedWorld(event);

            verify(callback, never()).run();
        }
    }

    @Test
    void onPlayerChangedWorld_legacyConstructor_doesNotNpe() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            when(sessionControl.isActive()).thenReturn(true);

            ReplayViewerManager mgr = createManager();
            PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
            when(event.getPlayer()).thenReturn(viewer);

            mgr.onPlayerChangedWorld(event); // must not throw NPE when callback is null
        }
    }
}
