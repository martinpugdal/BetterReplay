# Source Organization

A snapshot of the BetterReplay source layout and what's still open.

---

## Current Package Structure

```
me.justindevb.replay
├── api/
│   ├── ReplayAPI.java
│   ├── ReplayManager.java
│   └── events/
│       ├── RecordingSaveEvent.java
│       ├── RecordingStartEvent.java
│       ├── RecordingStopEvent.java
│       ├── ReplayStartEvent.java
│       └── ReplayStopEvent.java
├── listeners/
│   └── PacketEventsListener.java
├── recording/
│   ├── EntityTracker.java
│   ├── RecordingEventHandler.java
│   ├── RecordingPacketHandler.java
│   ├── TimelineBuilder.java
│   ├── TimelineEvent.java
│   └── TimelineEventAdapter.java
├── playback/
│   ├── FakeEntityManager.java
│   ├── PlaybackEngine.java
│   ├── ReplayBlockManager.java
│   ├── ReplayInteractionHandler.java
│   ├── ReplayInventoryUI.java
│   ├── ReplayViewerManager.java
│   └── SessionControl.java
├── entity/
│   ├── RecordedEntity.java
│   ├── RecordedEntityFactory.java
│   ├── RecordedMob.java
│   └── RecordedPlayer.java
├── snapshot/
│   ├── ChunkSnapshot.java
│   ├── WorldSnapshot.java
│   ├── WorldSnapshotCodec.java
│   └── WorldSnapshotter.java
├── storage/
│   ├── FileReplayStorage.java
│   ├── MySQLConnectionManager.java
│   ├── MySQLReplayStorage.java
│   ├── ReplayData.java
│   └── ReplayStorage.java
├── util/
│   ├── cache/
│   │   └── ReplayCache.java
│   ├── entity/
│   │   └── EntityTypeMapper.java
│   ├── integration/
│   │   └── FloodgateHook.java
│   ├── io/
│   │   ├── ItemStackSerializer.java
│   │   ├── ReplayCompressor.java
│   │   └── ReplayExporter.java
│   ├── model/
│   │   └── ReplayObject.java
│   ├── spawning/
│   │   ├── SpawnFakeMob.java
│   │   └── SpawnFakePlayer.java
│   └── version/
│       ├── UpdateChecker.java
│       └── VersionUtil.java
├── RecorderManager.java
├── RecordingSession.java
├── Replay.java
├── ReplayCommand.java
├── ReplayManagerImpl.java
├── ReplayRegistry.java
└── ReplaySession.java
```

---

## What's Working Well

- **API package** — `ReplayManager` interface and `ReplayAPI` facade provide a clean public contract for other plugins. The events sub-package follows standard Bukkit conventions.
- **Storage abstraction** — `ReplayStorage` interface with `FileReplayStorage` and `MySQLReplayStorage` implementations is well-designed and easy to extend. Now lives at top level alongside the new `ReplayData` value type.
- **Entity hierarchy** — `RecordedEntity` → `RecordedPlayer` / `RecordedMob` with `RecordedEntityFactory` uses proper polymorphism and factory pattern, in its own `entity/` package.
- **Recording / playback split** — Per-tick recording and playback responsibilities now live in focused classes under `recording/` and `playback/` instead of two ~600-line megaclasses.
- **Typed timeline events** — `TimelineEvent` is a sealed interface with one record per event type. The recording/playback contract is compile-time checked, and `TimelineEventAdapter` handles Gson (de)serialization.
- **World snapshots** — A dedicated `snapshot/` package captures and restores chunk state for replay, separate from the per-tick timeline pipeline.
- **Granular `util/`** — The old catch-all has been broken up into purpose-named sub-packages (`cache/`, `entity/`, `integration/`, `io/`, `model/`, `spawning/`, `version/`), so each utility lives next to others with the same role.

---

## Open Items

_None for now._
