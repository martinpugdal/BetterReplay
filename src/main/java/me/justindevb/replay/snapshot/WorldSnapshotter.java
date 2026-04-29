package me.justindevb.replay.snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Captures chunk snapshots around tracked players during recording so the
 * world state can be reproduced at playback time even if the surroundings
 * (e.g. a mine) have been reset since.
 *
 * <p>Snapshotting happens lazily at chunk boundaries: when a player enters a
 * chunk that has not been captured yet, the chunk (and its neighbours within
 * the configured radius) is captured. A hard cap on the number of chunks
 * guards against OOM on long-running recordings.</p>
 */
public final class WorldSnapshotter {

    private final WorldSnapshot snapshot = new WorldSnapshot();
    private final int radiusChunks;
    private final int maxChunks;
    private final Logger logger;
    private boolean capReached = false;

    public WorldSnapshotter(int radiusChunks, int maxChunks, Logger logger) {
        this.radiusChunks = Math.max(0, radiusChunks);
        this.maxChunks = Math.max(1, maxChunks);
        this.logger = logger;
    }

    public WorldSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isInsideSnapshot(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return snapshot.hasChunk(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public void expandAround(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = player.getWorld();

        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                tryCapture(world, playerChunkX + dx, playerChunkZ + dz);
            }
        }
    }

    private void tryCapture(World world, int cx, int cz) {
        if (capReached) return; // Avoid unnecessary checks once cap is reached.
        if (snapshot.hasChunk(world.getName(), cx, cz)) return;

        capReached = snapshot.size() >= maxChunks;
        if (capReached) {
            logger.warning("World snapshot chunk cap of " + maxChunks + " reached; skipping capture of chunk at "
                + world.getName() + " [" + cx + ", " + cz + "] and all subsequent chunks. Consider increasing the cap in config if this is a common occurrence.");
            return;
        }

        // Avoid forcing chunk loads — chunks far from the player are typically
        // loaded already by their view distance, and unloaded ones will be retried
        // on the next tick when expandAround() runs again.
        if (!world.isChunkLoaded(cx, cz)) return;

        Chunk chunk = world.getChunkAt(cx, cz);
        ChunkSnapshot captured = captureChunk(world, chunk);
        if (captured != null) {
            snapshot.addChunk(captured);
        }
    }

    private ChunkSnapshot captureChunk(World world, Chunk chunk) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int height = maxY - minY;
        int blockCount = 16 * height * 16;
        if (blockCount <= 0) return null;

        int[] indices = new int[blockCount];
        Map<String, Integer> paletteIndex = new HashMap<>();
        List<String> palette = new ArrayList<>();

        for (int dy = 0; dy < height; dy++) {
            int worldY = minY + dy;
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    BlockData data = chunk.getBlock(dx, worldY, dz).getBlockData();
                    String key = data.getAsString();
                    Integer idx = paletteIndex.get(key);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(key);
                        paletteIndex.put(key, idx);
                    }
                    int flatIdx = (dy * 256) + (dz * 16) + dx;
                    indices[flatIdx] = idx;
                }
            }
        }

        return new ChunkSnapshot(world.getName(), chunk.getX(), chunk.getZ(),
            minY, height, palette, indices);
    }
}
