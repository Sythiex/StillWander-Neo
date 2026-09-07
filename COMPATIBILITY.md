# Compatibility pass — Minecraft 1.21.1

Tested on 2026-09-06 using NeoForge 21.1.250 and Java 21.0.5. These are development smoke results, not full release qualification.

## Latest recorded verification

The latest automated follow-up, on 2026-09-07, passed **56 tests** and `clean test build --no-daemon --console=plain` (all ten tasks executed). It fixes loaded-sightline traversal overshooting mixed-direction chunk-corner endpoints. The new regression failed before the fix and now covers both traversal directions, positive and negative boundaries, multi-chunk segments, and missing endpoint chunks. In-game smoke checks were not rerun for this fix.

The latest combined automated and in-game passenger-camera run was completed on 2026-09-06:

| Check | Result | Evidence |
| --- | --- | --- |
| Clean automated gate | **54 tests passed**, no failures, errors, or skips | [Build, artifact hash, and counts](source/1.21.1/compat-evidence/passenger-build.json) |
| Standalone smoke | **14 checks passed** | [Standalone result](source/1.21.1/compat-evidence/passenger-none-smoke.txt) |
| Full pinned set smoke | **30 checks passed** | [Full-set result](source/1.21.1/compat-evidence/passenger-full-smoke.txt) |

The clean gate used `clean test build --no-build-cache --no-daemon --console=plain`: all ten Gradle tasks executed, including production/test compilation and tests. NeoForm reused its Minecraft preparation intermediates. Artifact inspection confirmed that both release and source JARs exclude smoke fixtures.

These files describe the recorded run and do not automatically validate later changes. Earlier runs and the before-fix failure are retained in [Evidence history](#evidence-history).

## Fixed test set

The machine-readable [manifest](source/1.21.1/compat-test-set.json) records exact artifact identifiers, download sources, and SHA-512 hashes. All eight downloaded distributions were checked against the recorded hashes; Modrinth distributions also matched the publisher API's hashes.

| Mod | Pinned version | Identifier |
| --- | --- | --- |
| Freecam | 1.3.0+mc1.21 | Modrinth `ROfcbxxe` |
| Dynamic FPS | 3.11.4 | Modrinth `T238FZpQ` |
| ReForgedPlay | 0.3 | CurseForge file `6942967` |
| Sable | 2.0.5 | Modrinth `U678xqle` |
| Create | 6.0.10 | Modrinth `UjX6dr61` |
| Create Aeronautics bundle | 1.3.2 | Modrinth `44pLdPGg` |
| Sodium | 0.8.13 | Modrinth `uMOpc5uV` |
| Iris | 1.8.14-beta.1 | Modrinth `KduFYu4t` |

Bundled dependencies discovered in the full run include Sable Companion 1.6.0, Flywheel 1.0.6, Ponder 1.0.82, Veil 4.3.2, Cloth Config 15.0.128, Create Simulated/Offroad 1.3.2, and the Forgified Fabric API modules carried by the renderer distributions. Still Wander embeds only Sable Companion 1.6.0, whose default provider supports running without Sable installed.

Sable 2.0.5 requires a newer Sodium line than the stable Iris 1.8.12/Sodium 0.6.13 pair, hence the pinned Iris beta. Test profiles load original distributions from `mods/`; putting the Sodium wrapper directly on the development runtime classpath caused its nested implementation to be skipped by dependency selection. The distributions are not modified.

## Repeatable checks

From the repository root:

```powershell
.\gradlew.bat -p source/1.21.1 clean test build --no-daemon --console=plain
.\gradlew.bat -p source/1.21.1 runClient -PsmokeTest -PcompatProfile=none --console=plain
.\gradlew.bat -p source/1.21.1 runClient -PsmokeTest -PcompatProfile=camera --console=plain
.\gradlew.bat -p source/1.21.1 runClient -PsmokeTest -PcompatProfile=full --console=plain
```

The smoke harness creates a new flat world in `source/1.21.1/run-smoke/<profile>/`, writes `stillwander-smoke-result.txt`, and closes Minecraft automatically. A failed or interrupted smoke run fails the Gradle task. Fixtures are in `src/smoke/java` and are excluded from both release and source JARs. Only the disposable fixture writes world blocks or drives replay APIs. Ordinary mod code remains client-only and reads loaded world state.

For interactive review, omit `-PsmokeTest`. Optional-mod runs use `run-compat/<profile>/`; the ordinary unprofiled client uses `run/`. Available profiles are `none`, `camera`, `replay`, `renderer`, `ships`, and `full`. Use their generated mod folders as fixed test instances; changing a profile's versions requires clearing its old test mod files to avoid duplicate versions.

## Verified behavior and practical limits

- **Automated gate:** retained upstream invariants, every configured mixin's target signature, actual Freecam/Replay/Dynamic FPS API signatures, session transitions, HUD ownership, non-finite input, moving obstacles, ship translation/rotation/interpolation, and loss of a ship frame.
- **Standalone:** smoke passed with only Still Wander, NeoForge, and the embedded companion available. Activation, capture, HUD restoration, idle reset, restart, and logout work without optional mod classes being present.
- **Freecam/Dynamic FPS:** enabling Freecam during capture releases the camera, recorder, HUD, and rendering request. Still Wander controls are inert during Freecam; disabling it starts fresh idle time. The transformed Dynamic FPS compatibility query returns the expected bypass state on entry and exit.
- **Full set:** a Sable platform was assembled, the player boarded it, and physics applied translation and angular velocity while the cinematic remained active. Checks covered global hull clearance, projected raycast hits, and a rig sampled at half-tick interpolation. Camera handoff checks also passed with Create Aeronautics, Sodium, and Iris loaded.
- **ReForgedPlay:** normal gameplay recording remained eligible. The harness then opened an actual recording, verified suppression immediately and during playback, paused it, sought to another time, and closed it. All phases kept Still Wander camera/capture/FPS state inactive. Pause checks run from render events because a paused replay stops game ticks.
- **Artifact:** Java 21 client classes, five client mixins, NeoForge metadata, the retained license, and a single nested Sable Companion dependency; no smoke fixtures, Fabric metadata, optional mod distributions, or ReplayMod timeline writer.

The original full smoke screenshot shows the player framed aboard the assembled platform with the HUD hidden. It verifies a rendered scene, not motion smoothness across frames. CSV files produced during the runs contain standalone Still Wander samples; replay writing remains absent from production code.

### Rendered ship hull and FPS shutdown regressions

- Render collision and raycast selection now use conservative bounds at the sampled render pose. Automated fixtures cover fast translation, intermediate rotation beyond both endpoint bounds, nearest terrain/ship hit ordering, and non-finite transforms. The in-game fixture temporarily displaces the previous client ship pose by 40 blocks and rotates it, verifies the rendered hull escapes the logical one-block padding, and checks actual hull clearance and a projected ray hit before restoring the pose.
- FPS release now belongs to the client session, after activation, idle, capture, and director cleanup. The in-game checks simulate unfocused and iconified window-observer state and assert Dynamic FPS's cached `powerState()` becomes `UNFOCUSED` after activity shutdown and `INVISIBLE` after a failed shot, immediately and after a repeated stop, without another status refresh. The fixture restores window-observer state afterward.

These fixtures do not replace visual fast-vehicle review or measurement of actual operating-system focus/minimize frame-rate limits.

### Seated camera orientation regression

The seated-player follow-up reproduced Sable 2.0.5 applying inherited ship rotation a second time through NeoForge's `Camera.setRotation(FFF)`. The old implementation failed the actual camera look-vector assertion by approximately 19 degrees; [seated-before-smoke.txt](source/1.21.1/compat-evidence/seated-before-smoke.txt) records the expected and observed vectors. Cinematic world-space orientation now writes the camera quaternion, basis vectors, Euler angles, and zero roll directly at the end of setup.

The latest full-set fixture mounts the player in an actual Create seat on the moving, rotated Sable platform, verifies nontrivial inherited rotation, and calls the transformed main camera's `setup` at partial ticks 0.25, 0.5, and 1.0 with a controlled world-space focus. It checks the actual look vector, quaternion, Euler angles, up/left basis, position, and zero roll, then compares the released camera with an ordinary seated camera. This checks camera state in a running game; it is not a visual motion-smoothness or dismount review.

### Passenger landscapes and travel recovery

Landscape shots previously kept world-space camera paths even when the passenger's ship moved. Wide/aerial panoramas now carry their authored viewing sweep in world compass axes, with zero camera roll, while other landscape compositions can follow a fixed world landmark from a moving camera. Landmark selection prefers approaching scenery to either side of actual travel. Existing procedural passage views retain their path-directed aim; same-ship subjects retain local rigs and tracking. External subjects, mixed groups, and close player fallbacks also use the passenger's carrier and live distance limits.

Sublevel shots survey the current surroundings at every cut, check loaded sightline chunks and moving geometry during sampling, and transport any safe fallback before revalidating it. A lost carried view gets a bounded replan and, if needed, a close player fallback without ending capture. Ordinary fatal failures still release the session and FPS request. Outside sublevels, the original selection weights, RNG draws, four-shot survey cadence, and shot execution remain in use.

The automated additions cover long translation, turns/banks and partial-tick interpolation, compass-stable authored pans, fixed world targets, independent external subject motion, local onboard tracking, transported fallbacks, target/frame loss, non-finite samples, distance limits, ahead/side preference, pass timing, comfort limits, and loaded sightlines across intermediate, negative, and corner-adjacent chunks. An absent-sublevel policy leaves subject selection untouched.

The full smoke fixture verifies that the real director installs carried shots for a seated passenger. It then uses controlled landscape plans and temporarily changes the real client ship poses to exercise translation, yaw, pitch, and bank at partial ticks 0.25, 0.5, and 1.0 through the transformed `Camera.setup`. Both the fixed landmark and compass-stable panorama passed position/orientation checks. Another check forces a carried view loss and verifies a safe replacement with capture and the FPS request still active. Existing fatal-shot FPS release and camera/replay handoffs also passed.

This is automated camera-state verification on the moving platform, not a visual flight review of a complex Aeronautics craft. Real chunk streaming during long flights, dismounts and transfers between ships, comfort/timing tuning, and the fifteen-minute performance baseline remain in the release matrix. The scenery preference uses the existing loaded surface search; this change does not expand its terrain search depth or distance.

## Evidence history

These earlier records are preserved unchanged. Their counts describe each historical run; use [Latest recorded verification](#latest-recorded-verification) for the most recent results.

| Pass | Recorded result | Evidence |
| --- | --- | --- |
| Original compatibility pass | 33 automated tests; 14 standalone, 18 camera-mod, and 25 full-set smoke checks passed | [Build](source/1.21.1/compat-evidence/build.json), [standalone](source/1.21.1/compat-evidence/none-smoke.txt), [camera mods](source/1.21.1/compat-evidence/camera-smoke.txt), [full set](source/1.21.1/compat-evidence/full-smoke.txt) |
| Reviewer regressions | 38 automated tests; 14 standalone and 27 full-set smoke checks passed; smoke fixtures excluded from both JARs | [Build](source/1.21.1/compat-evidence/review-build.json), [standalone](source/1.21.1/compat-evidence/review-none-smoke.txt), [full set](source/1.21.1/compat-evidence/review-full-smoke.txt) |
| Seated camera before fix | Failure reproduced: actual camera look vector differed from the world-space focus | [Before-fix result](source/1.21.1/compat-evidence/seated-before-smoke.txt) |
| Seated camera follow-up | 38 automated tests; 14 standalone and 28 full-set smoke checks passed | [Build](source/1.21.1/compat-evidence/seated-build.json), [standalone](source/1.21.1/compat-evidence/seated-none-smoke.txt), [full set](source/1.21.1/compat-evidence/seated-full-smoke.txt) |

## Remaining release matrix

Review real Aeronautics craft in translation and rotation, player seats and dismounts, moving block features, NPCs, hull/interior collisions, fast motion, ship unload/reload, and multiple ships. The fixture proves basic integration, not every vehicle assembly.

Run shader-pack shadow/auxiliary passes and inspect FOV, water, fog, and first-/third-person effects. The Iris auxiliary-pass guard is implemented; the smoke runs used no shader pack. Test Freecam tripod separately, replay timeline editing and video export, remote multiplayer, death/respawn, dimension travel, and Dynamic FPS unfocused/minimized limits. Replay export uses the same session gate but has not been rendered here.

Complete the eight-environment visual matrix and fifteen-minute performance comparison in [QUALITY.md](source/1.21.1/QUALITY.md). Unknown camera mods that replace the camera entity yield automatically; mods that only rewrite the existing camera need a specific detection API.

## API references

- [Sable Companion](https://github.com/ryanhcode/sable-companion) 1.6.0 provides the optional coordinate and ship-discovery API.
- [Sable](https://github.com/ryanhcode/sable) 2.0.5 provides the native clip pose stack and loaded ship collision queries.
- [Freecam](https://github.com/MinecraftFreecam/Freecam) exposes `Freecam.isEnabled()` for camera ownership, including tripod mode.
- [ReForgedPlay](https://www.curseforge.com/minecraft/mc-mods/reforgedplay-mod/files/6942967) exposes `ReplayModReplay.instance.getReplayHandler()` for replay sessions.
- [Dynamic FPS](https://github.com/juliand665/Dynamic-FPS) 3.11.4 exposes the NeoForge compatibility query and activity-free status refresh.
- [Iris](https://github.com/IrisShaders/Iris) exposes shader and shadow-pass state through its public API.
