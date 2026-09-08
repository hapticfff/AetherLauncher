# AetherLauncher — Full Launcher Specification

## Goal

A stable Android Minecraft Java launcher with real instance management, Java/runtime management, renderer selection, controls, mod discovery/install, and diagnostics.

## Core screens

- Home / Play
- Instances
- Instance details
- Mods recommendations
- Installed mods
- Downloads / tasks
- Accounts
- Java runtimes
- Renderer & graphics
- Controls
- Memory / JVM
- Storage
- General settings
- About / diagnostics

## Instance model

Each instance owns:

- Minecraft version
- loader (vanilla, Fabric, Forge, NeoForge when supported)
- loader version
- Java runtime
- renderer
- memory limits
- JVM arguments
- game arguments
- resolution / UI scale
- control profile
- environment variables
- game directory
- enabled mods
- resource packs
- shader packs
- backups

## Renderer settings

- Auto
- OpenGL ES 3 / Pojav bridge
- Vulkan/ANGLE when supported by the device/runtime
- renderer diagnostics
- FPS cap
- VSync
- resolution scale
- driver workaround flags

The launcher must never claim a backend is device-validated until it has successfully created a real Minecraft frame on-device.

## Java settings

- bundled Java runtimes
- detected installed runtimes
- per-instance Java selection
- architecture compatibility
- heap minimum/maximum
- GC selection
- extra JVM arguments
- environment variables
- reset-to-safe-profile

## Controls

- touch overlay
- button visibility
- button size and opacity
- button positions
- joystick settings
- mouse mode
- swipe sensitivity
- scroll sensitivity
- keyboard mapping
- controller/gamepad mapping
- per-instance control profiles

## Mods

The Mods screen should provide:

1. Recommendations relevant to the selected Minecraft version and loader.
2. Search and filtering.
3. Mod detail page with version compatibility.
4. Install/update/remove actions.
5. Dependency handling.
6. Enable/disable without deleting files.
7. Conflict warnings.
8. Per-instance isolation.
9. Offline visibility for already downloaded metadata/files.

Do not silently download arbitrary executable content. Downloads must be explicit and attributable to a known source.

## Launch lifecycle

Prepare -> validate -> resolve runtime -> resolve libraries/assets -> prepare natives -> configure renderer -> create surface -> start JVM -> bind GLFW -> launch Minecraft -> monitor -> graceful stop -> collect diagnostics.

Surface recreation must not require rebuilding the entire instance.

## Stability requirements

- no UI-thread blocking during downloads or runtime preparation
- cancellable downloads
- atomic file replacement
- checksum/size validation where metadata provides it
- recovery from interrupted installs
- bounded native bridge retries
- renderer fallback to the last known working backend
- crash report collection
- useful log viewer
- safe mode launch

## Optimization requirements

- cache dependency metadata
- avoid duplicate library downloads across instances
- deduplicate shared assets where safe
- lazy-load large lists
- keep renderer diagnostics lightweight
- persist settings atomically
- avoid unnecessary Surface/EGL recreation
- release native references on lifecycle teardown

## Current Phase 4.5 findings

CI is green for the latest renderer-host lifecycle commit, but CI does not constitute physical-device renderer validation. The native bridge currently resolves `libpojavexec.so` with `RTLD_NOLOAD` so it does not prematurely load the Pojav native library into the Android UI VM. The runtime packaging script still needs explicit verification that the required Pojav native bridge is included in the shipped runtime.

## Definition of done

A feature is complete only when:

- it is wired to persistent state;
- it survives process restart;
- it works for an actual instance;
- failures are surfaced to the user;
- CI builds successfully; and
- device-only behavior is explicitly marked as device-tested or untested.
