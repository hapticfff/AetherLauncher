# Aether Launcher

Kotlin + Jetpack Compose Android launcher for Minecraft Java Edition.

Current implementation includes Minecraft version installation, Android Java runtime bootstrapping, Android LWJGL/Pojav GLFW runtime preparation, and the Phase 4.4 Surface → GLFW/EGL → OpenGL ES rendering/input bridge.

Phase 4.4 now hands the Android `Surface` to Pojav's `libpojavexec` after the child JVM loads it, uses the real Pojav GLFW ABI for context creation/swap/pump, and forwards Android touch, keyboard, character, scroll, and resize events into GLFW callbacks.

APK builds are produced by GitHub Actions.
