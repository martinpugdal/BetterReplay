package me.justindevb.replay.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A recording-time snapshot of one or more chunks across one or more worlds.
 *
 * <p>Used during playback to restore the world state at the time of recording so
 * the viewer sees the original surroundings even if the live world has since been
 * reset (e.g. a regenerating mine).</p>
 */
public final class WorldSnapshot {

    private final Map<ChunkKey, ChunkSnapshot> chunks = new HashMap<>();

    public void addChunk(ChunkSnapshot chunk) {
        chunks.put(new ChunkKey(chunk.world(), chunk.chunkX(), chunk.chunkZ()), chunk);
    }

    public boolean hasChunk(String world, int chunkX, int chunkZ) {
        return chunks.containsKey(new ChunkKey(world, chunkX, chunkZ));
    }

    public ChunkSnapshot getChunk(String world, int chunkX, int chunkZ) {
        return chunks.get(new ChunkKey(world, chunkX, chunkZ));
    }

    public Collection<ChunkSnapshot> getChunks() {
        return chunks.values();
    }

    public int size() {
        return chunks.size();
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    /**
     * Returns true if the given block coordinates fall inside any captured chunk.
     */
    public boolean contains(String world, int blockX, int blockZ) {
        return hasChunk(world, blockX >> 4, blockZ >> 4);
    }

    /**
     * Returns the captured blockData at the given world coordinates, or {@code null}
     * if no chunk covers that location.
     */
    public String blockDataAt(String world, int x, int y, int z) {
        ChunkSnapshot chunk = getChunk(world, x >> 4, z >> 4);
        if (chunk == null) return null;
        return chunk.blockDataAt(x, y, z);
    }

    public List<ChunkSnapshot> chunksAsList() {
        return new ArrayList<>(chunks.values());
    }

    public record ChunkKey(String world, int chunkX, int chunkZ) {
    }
}
