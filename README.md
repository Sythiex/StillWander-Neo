<p align="center">
  <img src="assets/branding/still-wander-banner.jpg" alt="Still Wander — When you stop exploring, Still Wander doesn't" width="100%">
</p>

<h1 align="center">Still Wander</h1>

<p align="center">
  <strong>When you stop exploring, Still Wander doesn't.</strong><br>
  A cinematic AFK camera mod, ported to Minecraft 1.21.1 NeoForge.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/loader-NeoForge-d8c49a?style=flat-square" alt="NeoForge">
  <img src="https://img.shields.io/badge/environment-client--side-39b8c8?style=flat-square" alt="Client-side">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-d49a43?style=flat-square" alt="Minecraft 1.21.1">
</p>

Still Wander turns idle moments into a slow, cinematic journey through the world around you. Its environment-aware camera chooses varied player, landscape, cave, structure, and entity compositions, then moves between them with smooth pans, deliberate pacing, and independent cinematic FOV.

This fork ports the upstream 1.1 alpha source to NeoForge 1.21.1. It includes direct support for Sable/Create Aeronautics.

- **Scene-aware direction** adapts shots to open landscapes, forests, interiors, caves, weather, time, nearby entities, and terrain.
- **Cinematic movement** blends slow pans, tracking shots, wide establishing views, intimate details, and occasional static compositions.
- **Safe AFK behavior** returns control immediately when you move, interact, or take damage.
- **Client-side only** means nothing needs to be installed on the server.

<p align="center">
  <img src="assets/branding/still-wander-showcase.jpg" alt="Still Wander cinematic environments across the Overworld, caves, ocean, snow, and Nether" width="100%">
</p>

## In-game screenshots

<p align="center"><em>Captured in-game while Still Wander was active.</em></p>

<p align="center">
  <img src="assets/screenshots/still-wander-coastal-panorama.jpg" alt="A wide Still Wander camera shot following a Minecraft coastline" width="100%">
</p>

<table>
  <tr>
    <td width="50%"><img src="assets/screenshots/still-wander-boat-sunrise.jpg" alt="Still Wander framing the player in a boat at sunrise"></td>
    <td width="50%"><img src="assets/screenshots/still-wander-mesa-overlook.jpg" alt="Still Wander framing the player overlooking a mesa landscape"></td>
  </tr>
</table>

## Downloads and compatibility

Prebuilt downloads are not provided for this port. Download the JAR from a successful [GitHub Actions](#build-with-github-actions) run, or [build locally](#build-locally). The port targets Minecraft 1.21.1 and NeoForge 21.1.250 with Java 21.

Built-in integrations support **Freecam, Dynamic FPS, ReForgedPlay, Sable, and Iris**. Sable Companion 1.6.0 is bundled. See [COMPATIBILITY.md](COMPATIBILITY.md) for integration behavior, tested versions, and remaining checks.

While aboard a Sable sublevel, landscape cameras travel with you. Wide and aerial panoramas keep their compass orientation and a level horizon through ship turns and banks, while retaining their slow cinematic sweeps. Other landscape views prefer scenery ahead of travel and can watch it pass before cutting. Moving subjects and fallback views also stay within reach of the player. These adaptations apply only aboard sublevels; ordinary director behavior is retained.

The original Fabric release is available from [upstream Still Wander 1.0.0](https://github.com/Prasanna163/StillWander/releases/tag/v1.0.0).

## Install

1. Set up a Minecraft **1.21.1** client instance with **NeoForge**. NeoForge **21.1.250** is the tested version.
2. Obtain the mod JAR using [GitHub Actions](#build-with-github-actions) or [Build locally](#build-locally) below.
3. Copy `stillwander-neoforge-1.1.0-alpha.2+1.21.1.jar` into your instance's `mods/` folder.

## Controls

| Action | Default |
| --- | --- |
| Start or stop Still Wander immediately | `B` |
| Cut to the next shot | `N` |
| Open Still Wander settings | `F7` |
| Toggle uninterrupted capture mode | `F8` |
| Toggle director debug overlay | `F9` |
| Start automatically | Remain idle for 25 seconds |
| Exit normal cinematic mode | Move, interact, or take damage |

Keys can be changed from Minecraft's **Options > Controls > Key Binds** screen.

Still Wander yields to Freecam and replay viewing, including capture mode. Closing either starts a fresh idle countdown. ReForgedPlay recording during ordinary gameplay does not block cinematics. Still Wander never writes replay timelines or keyframes.

### Capturing footage and screenshots

`F8` starts Still Wander immediately in uninterrupted capture mode. In this mode, movement and normal player input do not stop the cinematic camera, making it suitable for recording video or composing screenshots.

For a session that also continues when the player takes damage, press `F7` first and turn **Exit on damage** off. Then press `F8`, start your preferred screen recorder, or use Minecraft's `F2` key to capture screenshots. Press `F8` again when you are finished.

Still Wander controls the cinematic camera; it does not encode an MP4 video itself. Use recording software such as OBS Studio, Xbox Game Bar, or another capture tool for video.

## Source

The NeoForge client source is in [`source/1.21.1`](source/1.21.1). Its version is declared in `source/1.21.1/gradle.properties`. The upstream [`v1.0.0`](https://github.com/Prasanna163/StillWander/tree/v1.0.0) tag preserves the authored Fabric implementation that reproduces the published 1.0.0 JAR.

The internal Java package and several class names still use `com.cinecraft`, the project's original working-title namespace. They are retained deliberately for binary traceability; the installed mod ID, resources, configuration, controls, and user-facing name are `stillwander` / Still Wander.

## Build

### Build with GitHub Actions

The manual [Build mod JAR workflow](.github/workflows/build-mod.yml) compiles the NeoForge port, runs its automated tests, and uploads the installable JAR on success. No local build tools are needed.

1. Open a GitHub repository containing this port where you have write access, such as your own fork. Open **Actions** and enable the workflow if prompted.
2. Select **Build mod JAR** in the workflow list.
3. Click **Run workflow**, select the branch **1.21.1-Neoforge**, and click **Run workflow** again.
4. Open the new run, select the **build** job, and expand **Run tests and build Still Wander** to view the build output. A successful run finishes with a green check and `BUILD SUCCESSFUL` in that step's log.
5. Once **Upload installable mod JAR** succeeds, return to the run's summary page. Under **Artifacts**, click the mod JAR artifact, currently `stillwander-neoforge-1.1.0-alpha.2+1.21.1.jar`, to download it.

Manual runs require `.github/workflows/build-mod.yml`, including its `workflow_dispatch` trigger, to be present on the repository's default branch. If **Run workflow** is missing, check that requirement and your write access. See [GitHub's manual workflow instructions](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

Artifacts are retained for 30 days, subject to repository retention limits or earlier deletion. If an artifact has expired, start a new run. The upload contains only the installable mod JAR; sources are excluded. In-game smoke tests and visual compatibility checks remain separate; see [COMPATIBILITY.md](COMPATIBILITY.md).

### Build locally

Requires Java 21 **JDK**. Clone this repository, or download and extract its source ZIP using **Code > Download ZIP**. Open a terminal in the repository root and run this command:

On Windows (PowerShell):

```powershell
.\gradlew.bat -p source/1.21.1 build
```

On Linux or macOS:

```bash
chmod +x ./gradlew
./gradlew -p source/1.21.1 build
```

Wait for `BUILD SUCCESSFUL`, then find the installable JAR in `source/1.21.1/build/libs/`. The build downloads the declared dependencies on first use, so an internet connection is required.

## License

The code is public for inspection and release verification but is not currently open-source licensed. See [LICENSE](LICENSE). A more permissive license can be chosen later without obscuring the present source history.

## Promotional artwork

<details>
  <summary><strong>View the Still Wander release poster</strong></summary>
  <br>
  <p align="center">
    <img src="assets/branding/still-wander-release-poster.jpg" alt="Still Wander release poster — The journey continues" width="620">
  </p>
</details>

The Still Wander name, logo, artwork, and distributed mod files are © 2026 Prasanna Kulkarni. All rights reserved.

Developed by [Prasanna163](https://github.com/Prasanna163) with generative-AI coding assistance.
