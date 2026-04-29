package me.justindevb.replay.playback;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class FakeEntityManagerTest {

    @Mock private Player viewer;

    @Test
    void track_thenIsTracked_returnsTrue() {
        FakeEntityManager mgr = new FakeEntityManager(viewer);

        mgr.track(42);

        assertTrue(mgr.isTracked(42));
    }

    @Test
    void untrack_removesTrackedId() {
        FakeEntityManager mgr = new FakeEntityManager(viewer);
        mgr.track(42);

        mgr.untrack(42);

        assertFalse(mgr.isTracked(42));
    }

    @Test
    void isTracked_unknownId_returnsFalse() {
        FakeEntityManager mgr = new FakeEntityManager(viewer);

        assertFalse(mgr.isTracked(99));
    }

    @Test
    void track_isIdempotent() {
        FakeEntityManager mgr = new FakeEntityManager(viewer);

        mgr.track(7);
        mgr.track(7);
        mgr.untrack(7);

        assertFalse(mgr.isTracked(7));
    }

    @Test
    void untrack_unknownId_isNoOp() {
        FakeEntityManager mgr = new FakeEntityManager(viewer);
        mgr.track(1);

        mgr.untrack(99);

        assertTrue(mgr.isTracked(1));
        assertFalse(mgr.isTracked(99));
    }
}
