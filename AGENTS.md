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

Start directly in furnishing. `RoomEntry.prepare` retires unfinished cleaning
on entry and in every saved home/undo snapshot without moving or repacking
furniture, awarding progress, or altering inventory, achievements or toy counts.
New homes have no cleaning stage. Keep the legacy persistence v11 reader and
cleaning model compatible, but never expose cleaning tools, grime, tutorial,
restart controls or a cleaning gate. `CleanAgain` must remain a no-op.

Default to Shared Space. The catalogue tray's `SpaceModeToggle` enters a real
Full Space via the existing `TidyFullSpace` Mixed Stage and can return to Shared.
The corner space-switch Augment is removed; keep space-mode observation in
`BindSpaceWindow` independent of the catalogue controls.
Keep `SpaceSession` process-owned. Space switches and the native Full Space
caption close must retain page, form drafts, active operations, scene models and
save data; ordinary Shared Space close must not reopen the app.
Old Stage destruction can arrive after re-entry. `stageDestroyed` must observe
the actual OS mode, never unconditionally close the latest Stage.

Regression coverage: `RoomEntryTest`, `CleaningNativeRuntimeTest`,
`SpaceModeNativeRuntimeTest`, `RoomDragNativeRuntimeTest`. Use isolated per-method
instrumentation on a separate preview package and verify its preference bytes
around each native test. Do not run fixture-based tests against user saves.
Check signing compatibility before replace-installing the production package;
if its signing key is unavailable, use a separate preview package and deliver
an unsigned release for the original signer. Relaunch the app after testing.
Headset/native assertions do not substitute for a person wearing the headset.

Single-hand room dragging must match catalogue extraction: use mapped local
pointer XYZ, keep the initial grab offset, and apply only the shared outer
window envelope. Do not clamp held objects to the floor, walls or furniture.
Keep catalogue controls enabled on a press/tap. Disable them only after actual
manipulation crosses the tap threshold or while a catalogue card is being held.
Resolve support/collision on release; invalid placement or tracking loss restores
the original layout and inventory. Raw tracked hand poses are reserved for
two-hand yaw/squeeze and room turns. Rebaseline when joining/leaving the pair;
its pivot stays fixed. Coverage: `RoomGestureInputTest`, `RoomStrictManipulationTest`,
`CardDropNativeRuntimeTest#placedModelsMatchCardFreePathAcrossRoomSurfaces`.

Keep runtime/debug scripts and logs in `/tmp`; final delivery artifacts belong in
the dated delivery directory. If recovery is needed, check `sboxbackup` first,
then use Finder Trash and manually drag the item back to its original path.
