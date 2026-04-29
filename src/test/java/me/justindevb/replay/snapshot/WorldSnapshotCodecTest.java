package me.justindevb.replay.snapshot;

import com.google.gson.JsonElement;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldSnapshotCodecTest {

    @Test
    void roundtrip_preservesPaletteAndIndices() {
        // 16 x 4 x 16 = 1024 blocks for a tiny snapshot
        int height = 4;
        int blockCount = 16 * height * 16;
        int[] indices = new int[blockCount];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i % 3;
        }
        ChunkSnapshot chunk = new ChunkSnapshot(
                "world", 5, -7, 0, height,
                List.of("minecraft:stone", "minecraft:diamond_ore", "minecraft:air"),
                indices
        );

        WorldSnapshot original = new WorldSnapshot();
        original.addChunk(chunk);

        JsonElement json = WorldSnapshotCodec.toJson(original);
        WorldSnapshot loaded = WorldSnapshotCodec.fromJson(json);

        assertNotNull(loaded);
        assertEquals(1, loaded.size());

        ChunkSnapshot loadedChunk = loaded.getChunk("world", 5, -7);
        assertNotNull(loadedChunk);
        assertEquals(0, loadedChunk.minY());
        assertEquals(height, loadedChunk.height());
        assertEquals(List.of("minecraft:stone", "minecraft:diamond_ore", "minecraft:air"),
                loadedChunk.palette());
        assertArrayEquals(indices, loadedChunk.indices());
    }

    @Test
    void emptySnapshot_serializesToJsonNull() {
        WorldSnapshot empty = new WorldSnapshot();
        JsonElement json = WorldSnapshotCodec.toJson(empty);
        assertTrue(json.isJsonNull());
    }

    @Test
    void blockDataAt_returnsCorrectStringForGivenCoord() {
        int height = 2;
        int[] indices = new int[16 * height * 16];
        // Mark a single coordinate as palette index 1
        // Index formula: (y * 256) + (z * 16) + x
        int targetX = 5, targetY = 1, targetZ = 8;
        int flat = ((targetY) * 256) + (targetZ * 16) + targetX;
        indices[flat] = 1;

        ChunkSnapshot chunk = new ChunkSnapshot(
                "world", 0, 0, 0, height,
                List.of("minecraft:stone", "minecraft:gold_ore"),
                indices
        );
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.addChunk(chunk);

        assertEquals("minecraft:gold_ore", snapshot.blockDataAt("world", targetX, targetY, targetZ));
        assertEquals("minecraft:stone", snapshot.blockDataAt("world", 0, 0, 0));
        assertNull(snapshot.blockDataAt("world", 100, 0, 0)); // outside chunk
    }
}
