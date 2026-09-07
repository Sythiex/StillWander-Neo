# Still Wander agent guide

This repository ports Still Wander to Minecraft 1.21.1 and NeoForge. Active development is under `source/1.21.1/`; internal Java packages retain the `com.cinecraft` namespace. The mod is client-only.

## Documentation

Read the documents relevant to the change before editing:

| Document | Use it for |
| --- | --- |
| [README.md](README.md) | Project overview, installation, controls, configuration, capture behavior, and basic build commands. |
| [PORTING.md](PORTING.md) | Upstream baseline, source-path mapping, platform adaptations, build status, and remaining port qualification. |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Optional-mod integrations, pinned test profiles, smoke-test commands, verified behavior, and remaining compatibility checks. |
| [QUALITY.md](source/1.21.1/QUALITY.md) | Automated checks, camera safety and ownership invariants, visual review, and performance expectations. |
| [compat-test-set.json](source/1.21.1/compat-test-set.json) | Exact optional-mod versions, artifact identifiers, download sources, and hashes. |
| [compat-evidence/](source/1.21.1/compat-evidence/) | Recorded build and smoke-test results; these describe the recorded run, not automatic validation of later changes. |
| [LICENSE](LICENSE) | Repository licensing terms. |

## Source layout

- `source/1.21.1/src/client/java/com/cinecraft/`: production client code, including the director, camera paths, compatibility adapters, and mixins.
- `source/1.21.1/src/main/resources/`: NeoForge metadata, mixin configuration, and assets.
- `source/1.21.1/src/test/`: automated regression tests and scene fixtures.
- `source/1.21.1/src/smoke/`: opt-in in-game test fixtures, excluded from release artifacts.
- `source/1.21.1/build.gradle` and `gradle.properties`: build configuration, target versions, dependencies, and development profiles.

## Validation and documentation updates

Run the Gradle wrapper from the repository root with `-p source/1.21.1`. The clean automated gate is:

```powershell
.\gradlew.bat -p source/1.21.1 clean test build --no-daemon --console=plain
```

Use `./gradlew` on Linux/macOS. Follow the linked quality and compatibility documents for checks appropriate to the change. Update those documents when behavior, dependencies, or validation status changes, and distinguish implemented behavior from behavior actually tested.
