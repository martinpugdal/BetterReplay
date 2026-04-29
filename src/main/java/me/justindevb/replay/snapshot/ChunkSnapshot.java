package me.justindevb.replay.snapshot;

import java.util.List;

/**
 * Immutable snapshot of a single chunk's block data at recording start.
 *
 * <p>Stores blocks as a palette of unique {@code blockData} strings plus a flat
 * {@code int[]} of palette indices. Indexing convention: for a block at world coords
 * {@code (x, y, z)} inside chunk {@code (cx, cz)}, the index is computed as:
 * {@code ((y - minY) * 256) + ((z & 15) * 16) + (x & 15)}.</p>
 */
public record ChunkSnapshot(String world, int chunkX, int chunkZ, int minY, int height, List<String> palette,
                            int[] indices) {

    public String blockDataAt(int worldX, int worldY, int worldZ) {
        if (worldY < minY || worldY >= minY + height) return null;
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        int localY = worldY - minY;
        int idx = (localY * 256) + (localZ * 16) + localX;
        if (idx < 0 || idx >= indices.length) return null;
        int paletteIdx = indices[idx];
        if (paletteIdx < 0 || paletteIdx >= palette.size()) return null;
        return palette.get(paletteIdx);
    }
}
