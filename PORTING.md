# NeoForge port status

This branch ports the checked-in Still Wander `1.1.0-alpha.1` director to Minecraft **1.21.1**, NeoForge **21.1.250**, and Java **21**. The green-build pass is followed by a compatibility pass with automated and in-game smoke coverage. The existing shot-selection and continuity algorithms are retained; coordinate handling, live validation, and session ownership have been adapted. Broad visual/performance qualification remains pending.

## Upstream baseline and layout

- Upstream: <https://github.com/Prasanna163/StillWander>
- Baseline: `16a73ed1194b49e2550942bb86e4137ed7a4ffdb` (`Implement deterministic continuity planning for 1.1 alpha`).
- Path mapping: upstream `source/1.21.11/` → this branch's `source/1.21.1/`.
- Java packages, relative client source paths, resources, JSON configuration, scene fixtures, licensing, and upstream attribution are retained.
- Build setup follows the supplied 1.21.1 template: ModDevGradle `2.0.146`, Gradle `9.2.1`, and Parchment `2024.11.17` for `1.21.1`.

For upstream updates, compare changes against the baseline first, translate the versioned path, and apply the Mojang naming equivalents used here. Keep algorithm changes separate from the platform adaptations listed below. The original Fabric source and release history remain available in Git.

## Adapted areas

- `build.gradle`, `settings.gradle`, and `neoforge.mods.toml`: native NeoForge build, client-only `@Mod` entry point, and client-only mixin list. No Fabric dependencies or metadata are packaged.
- `CinecraftClient`: NeoForge client ticks, key mapping registration, logout cleanup, and settings-screen registration. Leaving a world resets the idle countdown.
- `CinecraftConfig`: `FMLPaths.CONFIGDIR`, retaining `stillwander.json` and its format.
- `CinecraftSettingsScreen` and `CinecraftDebugHud`: vanilla 1.21.1 GUI APIs and NeoForge GUI layer registration.
- `CameraMixin`: 1.21.1 `Camera.setup(BlockGetter, ...)`, camera position shadows, and the `detached` flag. Cinematic world-space angles update the quaternion, basis vectors, Euler angles, and zero roll directly, bypassing Sable's additional seated-ship rotation in NeoForge's rotation setter.
- `GameRendererMixin`: `bobHurt`, `bobView`, and the 1.21.1 **double** FOV result.
- `KeyboardMixin` and `MouseMixin`: raw 1.21.1 callback signatures, preserving activity handling before ordinary input processing.
- `SceneScanner`, `SceneSubject`, camera paths, and tests: Yarn-to-Mojang names, 1.21.1 biome/weather queries, heightmaps, vectors, and entity APIs. Block light queries use NeoForge's context-aware light emission. Queries continue to use the client level, whose chunk cache only returns loaded chunks or empty placeholders; no forced loading or world writes were added.
- `CinematicRecorder`: standalone CSV and screenshot capture retained. Upstream ReplayMod timeline/keyframe writing is excluded by design.
- `RendererCompatibility`: optional Iris API queries exclude auxiliary shadow passes from camera and FOV overrides.
- `SessionGate` and `CameraCompatibility`: shared eligibility for controls, tick/render hooks, HUD/debug, capture, and FPS requests. Replay handlers, Freecam activity, camera-entity substitutions, world changes, and logout release session state and reset idle time. Missing installed replay/Freecam APIs disable cinematics and warn once.
- `TemporaryBooleanState`: HUD restoration only while the option still has the value written by Still Wander; observed external changes relinquish ownership.
- `DynamicFpsMixin` and `DynamicFpsCompatibility`: NeoForge compatibility-query bypass plus `onStatusChanged(false)` refresh, preserving Dynamic FPS user settings. The client session releases the rendering request after activation, idle, recorder, and director state are cleared, including failed-shot exits. The Fabric FREX bridge remains available to providers but is not the NeoForge integration mechanism.
- `WorldCoordinates`, `WorldAnchor`, and `ShipReferenceFrame`: bundled Sable Companion 1.6.0 provides optional ship discovery and logical/render transforms. Shots retain their rig in ship coordinates through translation and rotation. Entity offsets, moving block features, composition focus, surface/raycast results, fluid queries, and runtime clearance use the appropriate coordinates. Render samples snapshot all loaded ships once and conservatively transform their bounds into the sampled pose. Clearance and rays share these candidates; render rays clip terrain and ship-local geometry with Sable projection disabled and select the nearest global hit. Sample state is restored with `try/finally`; absent ship chunks and lost frames fail closed.
- `ValidatedShot` and `TrackedMovingShot`: every sampled camera is checked against current geometry; a formerly safe fallback cannot be reused after a moving obstruction occupies it.
- `SublevelCameraPolicy`, `StabilizedShipFrame`, and `CarriedShot`: passenger-only transport is independent of subject tracking. Landscape rigs follow a point near the passenger without inheriting ship rotation; wide/aerial panoramas retain their world-space viewing sweep, while other landscape views prefer approaching scenery and allow a short recession before cutting. Same-ship subjects retain local tracking, external subjects cannot carry the camera away, and fallback poses are transported and revalidated. The director has bounded carried-shot recovery and refreshes surveys at each sublevel shot. Ordinary selection, RNG draws, survey cadence, and execution remain on the original path; shared focus composition was extracted without changing its calculation.

## Build and validation

The latest automated verification, on 2026-09-07 after the loaded-sightline corner-endpoint fix, passed **56 freshly executed tests** and the clean build gate. All ten Gradle tasks executed; Minecraft preparation intermediates were reused. The latest in-game passenger-camera run, on 2026-09-06, passed **14 standalone smoke checks** and **30 full-set smoke checks** alongside its earlier 54-test gate. In-game smoke checks were not rerun for the corner fix. See [the latest results and evidence](COMPATIBILITY.md#latest-recorded-verification).

Artifact inspection checks NeoForge metadata, Java 21 bytecode, all five client mixins, the retained license, embedded Sable Companion, and exclusion of the Fabric metadata, ReplayMod writer, optional mod distributions, and development smoke fixtures. Regression coverage includes rendered hull candidate/raycast behavior, Dynamic FPS's resulting unfocused/iconified power state after shutdown, and the actual camera orientation with a player mounted in a Create seat on a rotated ship. Passenger regressions cover fixed landscape targets, compass-stable panoramas through ship turns/banks, carried fallbacks, loaded-view limits, and capture-preserving travel cuts.

The first green-build pass passed 17 tests. Records from the original compatibility pass, reviewer regressions, and the seated-camera failure before the fix are retained in [Evidence history](COMPATIBILITY.md#evidence-history).

From the repository root:

```powershell
.\gradlew.bat -p source/1.21.1 clean test build --no-daemon --console=plain
```

On Linux/macOS use `./gradlew` with the same arguments. The installable artifact is:

`source/1.21.1/build/libs/stillwander-neoforge-1.1.0-alpha.1+1.21.1.jar`

The `-sources.jar` is for development, not installation. `runClient` supports standalone and pinned compatibility profiles; see [COMPATIBILITY.md](COMPATIBILITY.md).

The automated gate carries forward all upstream tests and adds bytecode checks for every configured mixin and optional reflection target, session transitions, option ownership, ship transforms, and runtime camera safety. Separate smoke launches apply the mixins in a running game: standalone, Freecam/Dynamic FPS, and the full pinned set passed. The full run covered an assembled moving ship and actual replay playback, pause, and seeking. Follow [QUALITY.md](source/1.21.1/QUALITY.md) and the remaining matrix in [COMPATIBILITY.md](COMPATIBILITY.md) before release.

Development CI runs `clean test build` on relevant pushes and pull requests, compiling the code and running automated tests. The separate manual **Build mod JAR** workflow runs `clean build`, which includes the automated tests, then uploads the installable mod JAR on success. The JAR is available directly as a workflow artifact with 30-day retention; the sources JAR is excluded. See [Build with GitHub Actions](README.md#build-with-github-actions) for run and download instructions.

The manual archived-release workflow checks out `v1.0.0` to verify the original Fabric artifact using that tag's `versions/` directory. The current branch no longer bundles those JARs; the [upstream Fabric release](https://github.com/Prasanna163/StillWander/releases/tag/v1.0.0) remains available. Existing screenshots are retained upstream artifacts.

## Remaining qualification

The implementation supports moving-ship cinematics rather than suspending on ships. The current smoke fixture is a translating/rotating Sable platform with the player aboard, including an actual Create seat and assertions on the final camera orientation. Complex Aeronautics vehicles, seat motion and dismounts, nested contraptions, interiors, and live chunk unloading still need visual review. Iris was exercised without a shader pack, and ReForgedPlay export was not run. Freecam tripod follows the same enabled-state API but has not received its own visual session. Long-session performance, remote multiplayer, respawn/dimension transitions, and the environment matrix remain release gates. The 1.21.11 vanilla `InactivityFpsLimiter` mixin remains omitted because 1.21.1 has no such target.
