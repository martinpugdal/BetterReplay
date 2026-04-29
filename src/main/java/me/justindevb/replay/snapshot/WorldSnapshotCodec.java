package me.justindevb.replay.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes and deserializes {@link WorldSnapshot} to/from JSON.
 *
 * <p>Wire format (per chunk):
 * <pre>{@code
 * {
 *   "world": "world",
 *   "cx": 12,
 *   "cz": -7,
 *   "minY": -64,
 *   "height": 384,
 *   "palette": ["minecraft:stone", "minecraft:diamond_ore", ...],
 *   "data": "<base64 of gzip(little-endian int[])>"
 * }
 * }</pre>
 * The palette indices array is little-endian-int-encoded then gzipped, then base64'd.</p>
 */
public final class WorldSnapshotCodec {

    private WorldSnapshotCodec() {
    }

    public static JsonElement toJson(WorldSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return JsonNull.INSTANCE;

        JsonObject root = new JsonObject();
        JsonArray chunks = new JsonArray();
        for (ChunkSnapshot chunk : snapshot.getChunks()) {
            chunks.add(chunkToJson(chunk));
        }
        root.add("chunks", chunks);
        return root;
    }

    public static WorldSnapshot fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonObject()) return null;

        JsonObject root = element.getAsJsonObject();
        if (!root.has("chunks") || !root.get("chunks").isJsonArray()) return null;

        WorldSnapshot snapshot = new WorldSnapshot();
        for (JsonElement el : root.getAsJsonArray("chunks")) {
            if (!el.isJsonObject()) continue;
            ChunkSnapshot chunk = chunkFromJson(el.getAsJsonObject());
            if (chunk != null) snapshot.addChunk(chunk);
        }
        return snapshot.isEmpty() ? null : snapshot;
    }

    private static JsonObject chunkToJson(ChunkSnapshot chunk) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", chunk.world());
        obj.addProperty("cx", chunk.chunkX());
        obj.addProperty("cz", chunk.chunkZ());
        obj.addProperty("minY", chunk.minY());
        obj.addProperty("height", chunk.height());

        JsonArray palette = new JsonArray();
        for (String entry : chunk.palette()) palette.add(entry);
        obj.add("palette", palette);

        try {
            obj.addProperty("data", encodeIndices(chunk.indices()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode chunk snapshot data", e);
        }
        return obj;
    }

    private static ChunkSnapshot chunkFromJson(JsonObject obj) {
        if (!obj.has("world") || !obj.has("cx") || !obj.has("cz")
            || !obj.has("minY") || !obj.has("height")
            || !obj.has("palette") || !obj.has("data")) {
            return null;
        }

        String world = obj.get("world").getAsString();
        int cx = obj.get("cx").getAsInt();
        int cz = obj.get("cz").getAsInt();
        int minY = obj.get("minY").getAsInt();
        int height = obj.get("height").getAsInt();

        JsonArray paletteArr = obj.getAsJsonArray("palette");
        List<String> palette = new ArrayList<>(paletteArr.size());
        for (JsonElement el : paletteArr) {
            palette.add(el.isJsonNull() ? null : el.getAsString());
        }

        int[] indices;
        try {
            indices = decodeIndices(obj.get("data").getAsString());
        } catch (IOException e) {
            return null;
        }

        return new ChunkSnapshot(world, cx, cz, minY, height, palette, indices);
    }

    private static String encodeIndices(int[] indices) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(indices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int idx : indices) buffer.putInt(idx);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(buffer.array());
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static int[] decodeIndices(String base64) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(base64);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gzip.read(buf)) > 0) raw.write(buf, 0, n);
        }
        byte[] rawBytes = raw.toByteArray();
        ByteBuffer bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = rawBytes.length / 4;
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) indices[i] = bb.getInt();
        return indices;
    }
}
