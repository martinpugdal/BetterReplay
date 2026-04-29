package me.justindevb.replay;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.justindevb.replay.entity.RecordedEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayRegistryTest {

    private ReplayRegistry registry;
    @Mock private ReplaySession session1;
    @Mock private ReplaySession session2;

    @BeforeEach
    void setUp() {
        registry = new ReplayRegistry();
        // Clean state
        for (ReplaySession s : registry.getActiveSessions().toArray(new ReplaySession[0])) {
            registry.remove(s);
        }
    }

    @AfterEach
    void tearDown() {
        for (ReplaySession s : registry.getActiveSessions().toArray(new ReplaySession[0])) {
            registry.remove(s);
        }
    }

    @Test
    void add_containsSession() {
        registry.add(session1);
        assertTrue(registry.contains(session1));
    }

    @Test
    void remove_noLongerContains() {
        registry.add(session1);
        registry.remove(session1);
        assertFalse(registry.contains(session1));
    }

    @Test
    void getActiveSessions_returnsAll() {
        registry.add(session1);
        registry.add(session2);
        assertEquals(2, registry.getActiveSessions().size());
    }

    @Test
    void getEntityById_findsAcrossSessions() {
        RecordedEntity entity = mock(RecordedEntity.class);
        // Use lenient() because ConcurrentHashMap iteration order is non-deterministic;
        // session2 may be checked first, making session1's stub unused.
        lenient().when(session1.getRecordedEntity(42)).thenReturn(null);
        when(session2.getRecordedEntity(42)).thenReturn(entity);

        registry.add(session1);
        registry.add(session2);

        assertSame(entity, registry.getEntityById(42));
    }

    @Test
    void getEntityById_notFound_returnsNull() {
        when(session1.getRecordedEntity(anyInt())).thenReturn(null);
        registry.add(session1);

        assertNull(registry.getEntityById(999));
    }

    @Test
    void concurrentAddRemove_doesNotThrow() throws InterruptedException {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            ReplaySession s = mock(ReplaySession.class);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        registry.add(s);
                        registry.contains(s);
                        registry.getActiveSessions().size();
                        registry.remove(s);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();
    }

    @Test
    void getSessionForViewer_returnsMatchingSession() {
        Player viewer = mock(Player.class);
        when(session1.getViewer()).thenReturn(viewer);

        registry.add(session1);

        assertSame(session1, registry.getSessionForViewer(viewer));
    }

    @Test
    void getSessionForViewer_returnsNullWhenNoMatch() {
        Player viewer1 = mock(Player.class);
        Player viewer2 = mock(Player.class);
        when(session1.getViewer()).thenReturn(viewer1);

        registry.add(session1);

        assertNull(registry.getSessionForViewer(viewer2));
    }

    @Test
    void getSessionForViewer_returnsNullWhenEmpty() {
        Player viewer = mock(Player.class);
        assertNull(registry.getSessionForViewer(viewer));
    }
}
