package me.justindevb.replay.playback;

import java.util.ArrayList;
import java.util.List;
import me.justindevb.replay.Replay;
import me.justindevb.replay.recording.TimelineEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ReplayBlockManagerBoundaryTest {

    @Mock private Player viewer;
    @Mock private Replay replay;

    @Test
    void applyReplayBlockChangesInRange_clampsIndicesAndDispatchesExpectedEvents() {
        RecordingBlockManager manager = new RecordingBlockManager(viewer, replay);
        List<TimelineEvent> timeline = List.of(
                new TimelineEvent.BlockPlace(1, "u", "world", 0, 64, 0, "minecraft:stone", null),
                new TimelineEvent.BlockBreakStage(2, "u", "world", 0, 64, 0, 5),
                new TimelineEvent.BlockBreak(3, "u", "world", 0, 64, 0, "minecraft:stone")
        );

        manager.applyReplayBlockChangesInRange(-50, 999, timeline);

        assertEquals(2, manager.blockChanges.size());
        assertEquals(1, manager.stages.size());
    }

    @Test
    void applyReplayBlockChangesInRange_reversedRangeIsNoOp() {
        RecordingBlockManager manager = new RecordingBlockManager(viewer, replay);
        List<TimelineEvent> timeline = List.of(
                new TimelineEvent.BlockPlace(1, "u", "world", 0, 64, 0, "minecraft:stone", null),
                new TimelineEvent.BlockBreak(2, "u", "world", 0, 64, 0, "minecraft:stone")
        );

        manager.applyReplayBlockChangesInRange(2, 1, timeline);

        assertEquals(0, manager.blockChanges.size());
        assertEquals(0, manager.stages.size());
    }

    private static final class RecordingBlockManager extends ReplayBlockManager {
        private final List<TimelineEvent> blockChanges = new ArrayList<>();
        private final List<TimelineEvent.BlockBreakStage> stages = new ArrayList<>();

        private RecordingBlockManager(Player viewer, Replay replay) {
            super(viewer, replay);
        }

        @Override
        public void applyReplayBlockChange(TimelineEvent event, boolean immediateBreakRemoval) {
            blockChanges.add(event);
        }

        @Override
        public void showGlobalBlockBreakStage(TimelineEvent.BlockBreakStage event) {
            stages.add(event);
        }
    }
}

