package me.justindevb.replay.playback;

/**
 * Callback interface for actions that components in {@code playback/} delegate
 * back to the owning {@code ReplaySession}.
 */
public interface SessionControl {
    void togglePause();

    void skipSeconds(int seconds);

    void stop();

    boolean isActive();
}
