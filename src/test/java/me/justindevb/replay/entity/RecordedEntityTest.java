package me.justindevb.replay.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordedEntityTest {

    @Test
    void respawn_withCurrentLocation_callsSpawn() {
        RecordedEntity entity = mock(RecordedEntity.class);
        Location loc = new Location(mock(World.class), 1, 2, 3);
        when(entity.isDestroyed()).thenReturn(false);
        when(entity.getCurrentLocation()).thenReturn(loc);
        doCallRealMethod().when(entity).respawn();

        entity.respawn();

        verify(entity).spawn(loc);
    }

    @Test
    void respawn_nullLocation_isNoOp() {
        RecordedEntity entity = mock(RecordedEntity.class);
        when(entity.isDestroyed()).thenReturn(false);
        when(entity.getCurrentLocation()).thenReturn(null);
        doCallRealMethod().when(entity).respawn();

        entity.respawn();

        verify(entity, never()).spawn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void respawn_destroyedEntity_isNoOp() {
        RecordedEntity entity = mock(RecordedEntity.class);
        when(entity.isDestroyed()).thenReturn(true);
        doCallRealMethod().when(entity).respawn();

        entity.respawn();

        verify(entity, never()).spawn(org.mockito.ArgumentMatchers.any());
    }
}
