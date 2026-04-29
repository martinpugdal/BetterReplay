package me.justindevb.replay.playback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.justindevb.replay.entity.RecordedEntity;
import me.justindevb.replay.recording.TimelineEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybackEngineBoundaryTest {

    @Mock private ReplayBlockManager blockManager;
    @Mock private FakeEntityManager fakeEntityManager;
    @Mock private RecordedEntity entity;

    private Set<UUID> deadEntities;
    private Map<UUID, RecordedEntity> recordedEntities;
    private PlaybackEngine engine;

    @BeforeEach
    void setUp() {
        deadEntities = new HashSet<>();
        recordedEntities = new HashMap<>();
        engine = new PlaybackEngine(deadEntities, recordedEntities, blockManager, fakeEntityManager);
    }

    @Test
    void handleEvent_blockPlace_delegatesToBlockManager() {
        TimelineEvent.BlockPlace place = new TimelineEvent.BlockPlace(10, "u", "world", 1, 2, 3, "minecraft:stone", null);

        engine.handleEvent(entity, place);

        verify(blockManager).applyReplayBlockChange(place, false);
    }

    @Test
    void handleEvent_entityDeath_marksDeadAndRemovesEntity() {
        UUID uuid = UUID.randomUUID();
        when(entity.getUuid()).thenReturn(uuid);
        recordedEntities.put(uuid, entity);

        TimelineEvent.EntityDeath death = new TimelineEvent.EntityDeath(20, uuid.toString(), "ZOMBIE", "world", 0, 0, 0);
        engine.handleEvent(entity, death);

        verify(entity).showDeath();
        verify(entity).destroy();
        assertTrue(deadEntities.contains(uuid));
        assertFalse(recordedEntities.containsKey(uuid));
    }

    @Test
    void handleEvent_playerQuit_untracksFakeEntityAndDestroys() {
        UUID uuid = UUID.randomUUID();
        // PlayerQuit handler resolves the UUID from the event payload, not the RecordedEntity,
        // so we only need entity.getFakeEntityId() stubbed for the FakeEntityManager.untrack call.
        when(entity.getFakeEntityId()).thenReturn(1337);
        recordedEntities.put(uuid, entity);

        TimelineEvent.PlayerQuit quit = new TimelineEvent.PlayerQuit(30, uuid.toString());
        engine.handleEvent(entity, quit);

        verify(entity).destroy();
        assertFalse(recordedEntities.containsKey(uuid));
        verify(fakeEntityManager).untrack(1337);
    }
}
