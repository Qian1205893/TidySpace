# TidySpace

Existing PICO Spatial Android/Kotlin app. Package `com.example.tidyspace.pc5853b2a`;
launch activity `.platform.LaunchActivity`; Spatial SDK 6.1.9.

Use the current project and its existing fixed-volume room. Preserve the latest
`tidy-room` preferences, furniture poses, inventory, achievements and toy counts.
Replace-install APKs; do not uninstall or replace user saves with test fixtures.

Build with JDK 21:

```sh
JAVA_HOME="/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home" ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Cleaning behavior lives in `CleaningRules`, `CleaningGrid`, `CleaningVisibility`
and `CleaningState`. The 8x3 visibility footprint matches the alpha cells in
`models/tidy-renovation.glb`; update and verify it if that asset changes.
Transparent padding cannot block completion. Vacuum accepts loose debris and
supports bounded, local suction while held. Pair turns lock translation and
pause suction. Cancel rolls back uncommitted work.

Persistence v11 stores raw cell means. Only accepted completion canonicalizes
cleaning progress and cells to one, preserving compatibility with older readers.
Loading a visually complete old save unlocks furnishing and persists that repair.
All non-cleaning state must remain unchanged.

`RoomScene.applyInput` applies cleaning visibility immediately through
`RoomRenovation.applyCleaning`. Cache material opacity rather than reading and
resubmitting it on every input frame. Keep completed action overlays hidden.

Regression coverage: `CleaningPathTest`, `CleaningRulesTest`,
`CleaningNativeRuntimeTest`, `CleaningPlayNativeRuntimeTest`,
`RoomDragNativeRuntimeTest`. Use isolated per-method instrumentation and verify
user preference bytes around each native test. Relaunch the app after testing.
Headset/native assertions do not substitute for a person wearing the headset.

Single-hand room dragging must match catalogue extraction: use mapped local
pointer XYZ, keep the initial grab offset, and apply only the shared outer
window envelope. Do not clamp held objects to the floor, walls or furniture.
Resolve support/collision on release; invalid placement or tracking loss restores
the original layout and inventory. Raw tracked hand poses are reserved for
two-hand yaw/squeeze and room turns. Rebaseline when joining/leaving the pair;
its pivot stays fixed. Keep cleaning contact/work checks independent of visible
tool motion. Coverage: `RoomGestureInputTest`, `RoomStrictManipulationTest`,
`CardDropNativeRuntimeTest#placedModelsMatchCardFreePathAcrossRoomSurfaces`.

Keep runtime/debug scripts and logs in `/tmp`; final delivery artifacts belong in
the dated delivery directory. If recovery is needed, check `sboxbackup` first,
then use Finder Trash and manually drag the item back to its original path.
