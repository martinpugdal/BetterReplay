package me.justindevb.replay.util.version;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import me.justindevb.replay.recording.TimelineEvent;
import me.justindevb.replay.snapshot.WorldSnapshot;
import me.justindevb.replay.snapshot.WorldSnapshotCodec;
import me.justindevb.replay.storage.ReplayData;

public final class VersionUtil {

    /**
     * Minimum plugin version required to read recordings produced by this build.
     */
    public static final String MIN_RECORDING_VERSION = "1.4.0";

    private static final Type TIMELINE_LIST_TYPE = new TypeToken<List<TimelineEvent>>() {
    }.getType();

    private VersionUtil() {
    }

    /**
     * Returns true if {@code running} is greater than or equal to {@code required}.
     * Compares dot-separated integer segments (e.g. "1.4.0" >= "1.4.0").
     */
    public static boolean isAtLeast(String running, String required) {
        String[] r = running.split("\\.");
        String[] q = required.split("\\.");
        int len = Math.max(r.length, q.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? parseSegment(r[i]) : 0;
            int qv = i < q.length ? parseSegment(q[i]) : 0;
            if (rv != qv) return rv > qv;
        }
        return true;
    }

    private static int parseSegment(String s) {
        // Strip non-numeric suffixes like "-SNAPSHOT"
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Wraps a timeline list in an envelope JSON object with version metadata.
     * Convenience overload that records no world snapshot — equivalent to
     * {@link #wrapReplayData(Gson, ReplayData, String)} with a null snapshot.
     */
    public static String wrapTimeline(Gson gson, List<?> timeline, String pluginVersion) {
        @SuppressWarnings("unchecked")
        List<TimelineEvent> typed = (List<TimelineEvent>) timeline;
        return wrapReplayData(gson, new ReplayData(typed, null), pluginVersion);
    }

    /**
     * Parses replay JSON into a timeline list, discarding any embedded world snapshot.
     * Convenience overload retained for compatibility with callers that pre-date
     * snapshot support. The {@code listType} parameter is accepted for signature
     * compatibility with the previous version of this method but is not used
     * internally; the timeline type is always {@code List<TimelineEvent>}.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> parseReplayJson(Gson gson, String json, String runningVersion,
                                              @SuppressWarnings("unused") Type listType) {
        return (List<T>) parseReplayData(gson, json, runningVersion).timeline();
    }

    /**
     * Wraps a replay payload (timeline + optional world snapshot) in an envelope JSON
     * object with version metadata.
     */
    public static String wrapReplayData(Gson gson, ReplayData data, String pluginVersion) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("createdBy", pluginVersion);
        envelope.addProperty("minVersion", MIN_RECORDING_VERSION);
        envelope.add("timeline", gson.toJsonTree(data.timeline()));
        if (data.worldSnapshot() != null && !data.worldSnapshot().isEmpty()) {
            envelope.add("worldSnapshot", WorldSnapshotCodec.toJson(data.worldSnapshot()));
        }
        return gson.toJson(envelope);
    }

    /**
     * Parses replay JSON, handling legacy (raw array), envelope (timeline only), and
     * envelope + worldSnapshot formats. Checks version compatibility when an envelope
     * is present.
     *
     * @throws ReplayVersionMismatchException if the recording requires a newer plugin version
     */
    public static ReplayData parseReplayData(Gson gson, String json, String runningVersion) {
        JsonElement el = JsonParser.parseString(json);

        if (el.isJsonArray()) {
            // Legacy format: raw timeline array, no version check possible
            List<TimelineEvent> timeline = gson.fromJson(el, TIMELINE_LIST_TYPE);
            return new ReplayData(timeline, null);
        }

        JsonObject obj = el.getAsJsonObject();
        if (obj.has("minVersion")) {
            String required = obj.get("minVersion").getAsString();
            if (!isAtLeast(runningVersion, required)) {
                throw new ReplayVersionMismatchException(required, runningVersion);
            }
        }

        List<TimelineEvent> timeline = gson.fromJson(obj.get("timeline"), TIMELINE_LIST_TYPE);

        WorldSnapshot snapshot = null;
        if (obj.has("worldSnapshot")) {
            snapshot = WorldSnapshotCodec.fromJson(obj.get("worldSnapshot"));
        }
        return new ReplayData(timeline, snapshot);
    }

    /**
     * Thrown when a recording requires a newer plugin version than is currently running.
     */
    public static class ReplayVersionMismatchException extends RuntimeException {
        private final String requiredVersion;
        private final String runningVersion;

        public ReplayVersionMismatchException(String requiredVersion, String runningVersion) {
            super("Recording requires BetterReplay v" + requiredVersion + "+, running v" + runningVersion);
            this.requiredVersion = requiredVersion;
            this.runningVersion = runningVersion;
        }

        public String getRequiredVersion() {
            return requiredVersion;
        }

        public String getRunningVersion() {
            return runningVersion;
        }
    }
}

