# Still Wander quality gates

This file defines the development contract for the 1.21.1 NeoForge port of the cinematic director. A feature is not complete merely because it compiles. Current implementation and qualification results are recorded in [PORTING.md](../../PORTING.md) and [COMPATIBILITY.md](../../COMPATIBILITY.md).

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat -p source/1.21.1 clean test build --no-daemon --console=plain
```

The automated suite covers deterministic continuity rules, bounded state, camera-path invariants, diagnostic traces, and the scene-fixture catalogue. It also checks every configured mixin's injection signatures and shadow members against the pinned Minecraft/NeoForge bytecode without launching Minecraft. The automatic [Test workflow](../../.github/workflows/build-and-test.yml) runs `clean test` on every relevant push and pull request, compiling and testing without packaging mod JARs. The manual [Build mod JAR workflow](../../.github/workflows/build-mod.yml) runs `clean build`, which includes those tests, and uploads the installable JAR only after success. Archived Fabric published-JAR parity is a separate manual workflow using the upstream `v1.0.0` tag.

## Safety invariants

- Auto-start camera controls only idle-triggered activation and its rendering request. Off must leave manual and capture controls eligible, stop any automatic session through normal cleanup, and persist across restarts. Changing the option resets idle time without cancelling manual/capture sessions; re-enabling waits a full idle delay. Configs without the option and Reset retain the default On behavior.
- Collision and visibility validation remains authoritative over editorial scoring.
- Camera and focus samples must be finite.
- FOV tracks must remain inside the planner's supported range.
- Damage, activity, manual controls, HUD restoration, and Dynamic FPS request/release semantics must not be changed by director work. Camera handoff must release capture and FPS requests, respect observed external HUD changes, and restart idle time. Synchronous FPS release observers must see all cinematic state cleared; test the resulting Dynamic FPS power state, not only its bypass query.
- Replay playback, pause, seeking, editing, and export must never acquire Still Wander camera/control ownership. Ordinary recording is eligible. No replay files, packets, or timelines may be changed by the mod.
- Ship camera rigs retain local offsets across logical and render poses. Cinematic angles are world-space: the final camera quaternion, direction vectors, and Euler angles must face the planned focus without inheriting a mounted seat's ship rotation again. Verify the transformed camera with an actually seated player on a rotated ship, and verify ordinary camera behavior resumes after ownership ends. Missing ship geometry or non-finite positions must fail closed, and moving obstacles invalidate previously safe poses. Collision and raycast candidates must include the sampled rendered hull even when translation or intermediate rotation places it outside logical bounds and fixed padding.
- The scanner must inspect loaded world state only and must not mutate the world.
- Aboard sublevels, camera transport is independent of subject tracking. Landscape offsets stay aligned with world axes while following a passenger-adjacent point on the ship. Panoramas retain authored world-space sweeps without inheriting pilot yaw, pitch, or roll; fixed landmarks remain in world space. Other ships and mixed groups must not drag the rig away from the passenger. Same-ship subjects may retain local rigs and local tracking.
- Sublevel shots recheck distance from the live player, loaded sightline chunks, and collision clearance for every returned pose, including transported fallbacks. A failed carried view may trigger one fresh plan and a close player fallback; it must never reuse a frozen world-space camera or force chunk loading. Unrelated fatal-shot failures retain ordinary session shutdown and FPS-release semantics. Boarding, leaving, changing, or losing a ship invalidates the previous carrier.
- Sublevel surveys refresh at each new shot; ordinary surveys keep the four-shot cadence. Sublevel selection and execution must not consume extra random draws outside sublevels. Forward scenery preference uses actual horizontal travel, has no directional bias below 0.5 blocks/second, and remains subordinate to view safety. Passing shots allow a two-second recession, subject to their ordinary duration and safety limits; close passes above 30 degrees/second estimated angular motion are rejected.
- The existing planner remains the fallback when no continuity-aware candidate is safe.

## Fixed visual matrix

Milestone builds are reviewed in these environments before release:

1. Open plains or desert
2. Dense forest
3. Small interior
4. Cave
5. Ocean or coast
6. Village or player build
7. Nether
8. Moving nearby entity

For each environment, review smoothness, composition, continuity, variety, collision safety, FOV changes, and subject tracking. Visual review is a milestone gate; ordinary code changes use automated validation and do not require launching Minecraft.

## Performance baseline

The opt-in debug overlay reports scene-survey and shot-planning time in microseconds. The director also retains the latest 32 accepted `ShotTrace` records for diagnostic integrations. Compare median and worst observed values over a fifteen-minute session with the last accepted milestone. A regression must be investigated before release; raising scan or candidate budgets requires its own reviewed change.

The passenger policy increases survey frequency only aboard sublevels and performs additional loaded-chunk checks during sampling. Include long flights and repeated safety cuts in this performance review. Unit and smoke results do not establish a fifteen-minute performance baseline or passenger comfort on complex piloted craft.

## Phase 1 continuity contract

Continuity is a soft scoring layer. It may reward an adjacent scale change or a short subject continuation and penalize repeated scale, repeated orbit, large lens jumps, uncontrolled action-axis crossings, and overused subjects. It may never approve a candidate rejected by collision, visibility, or framing checks.
