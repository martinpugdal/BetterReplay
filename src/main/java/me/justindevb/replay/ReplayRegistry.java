package me.justindevb.replay;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.justindevb.replay.entity.RecordedEntity;
import org.bukkit.entity.Player;

public class ReplayRegistry {
    private final Set<ReplaySession> activeSessions = ConcurrentHashMap.newKeySet();

    public void add(ReplaySession session) {
        activeSessions.add(session);
    }

    public void remove(ReplaySession session) {
        activeSessions.remove(session);
    }

    public boolean contains(ReplaySession session) {
        return activeSessions.contains(session);
    }

    public RecordedEntity getEntityById(int id) {
        for (ReplaySession session : activeSessions) {
            RecordedEntity e = session.getRecordedEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public Set<ReplaySession> getActiveSessions() {
        return activeSessions;
    }

    /**
     * Returns the first active session for the given viewer, or null if none.
     */
    public ReplaySession getSessionForViewer(Player viewer) {
        for (ReplaySession session : activeSessions) {
            if (session.getViewer().equals(viewer)) {
                return session;
            }
        }
        return null;
    }
}
