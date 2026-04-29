package me.justindevb.replay.util.cache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReplayCache {

    private final List<String> replays = new CopyOnWriteArrayList<>();

    public List<String> getReplays() {
        return List.copyOf(replays);
    }

    public void setReplays(List<String> names) {
        replays.clear();
        replays.addAll(names);
    }
}



