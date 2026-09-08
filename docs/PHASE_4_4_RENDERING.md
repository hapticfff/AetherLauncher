# Phase 4.4 — Real Minecraft Rendering

## Objective

Make the installed Minecraft Java client render continuously through AetherLauncher's Android `SurfaceView` instead of only proving that Android can create an EGL/OpenGL ES surface.

## Rendering pipeline

```text
Minecraft client
    │
    ▼
LWJGL 3 Java classes
    │ JNI/native library loading
    ▼
Aether GLFW 3 compatibility layer
    │
    ▼
Android ANativeWindow
    │
    ▼
EGLDisplay + EGLSurface + GLES 3 context
    │
    ▼
Android SurfaceView
```

## Ownership model

- `RendererSurfaceView` owns the Android `Surface` lifecycle.
- Native code acquires an `ANativeWindow` reference while the surface is attached.
- The Minecraft/LWJGL thread owns the GLFW window and EGL current context.
- Android UI callbacks only enqueue input/lifecycle/resize events; they never call GLFW callbacks directly.
- `glfwPollEvents()` drains the queue on the Minecraft thread and invokes registered GLFW callbacks in order.
- Surface destruction invalidates the window surface before releasing the Android window reference.
- A recreated Android surface must create a fresh EGL window surface while preserving the Java/Minecraft process when possible.

## Milestone slices

### 4.4.1 — GLFW compatibility contract

Implement the exact GLFW entry points used by the selected LWJGL build. Start with initialization, window creation/destruction, context current, framebuffer/window sizing, swap buffers/interval, timing, event polling, callbacks, and error reporting.

Do not claim compatibility from function names alone: callback signatures, calling conventions, return values, constants, and JNI/native library names must match the target LWJGL binary.

### 4.4.2 — Live EGL window

Move from the current one-frame clear test to a persistent `EGLSurface` owned by the GLFW window. `glfwSwapBuffers()` must operate on that live surface every frame.

Validate and log:

- `GL_VERSION`
- `GL_VENDOR`
- `GL_RENDERER`
- `GLSL` version
- required GLES extensions
- EGL initialization/config/context/surface errors

### 4.4.3 — Minecraft/LWJGL binding

Launch the selected Minecraft client in the same native process that owns the Android renderer bridge, or establish an explicit IPC/shared-surface design if the runtime architecture requires a separate process.

The LWJGL native library search path must resolve Aether's Android-compatible GLFW/OpenGL libraries before desktop GLFW natives.

### 4.4.4 — Event delivery

Translate Android events to GLFW semantics on the Minecraft thread:

- touch → cursor position + mouse button semantics
- hardware keys → GLFW key/scancode/modifier callbacks
- text input → GLFW character callback
- scroll → GLFW scroll callback
- focus → window focus callback
- resize → window/framebuffer size callbacks

Keep events ordered and bounded. Overflow should be logged rather than silently hiding sustained input pressure.

### 4.4.5 — Lifecycle and resize

Handle `surfaceCreated`, `surfaceChanged`, `surfaceDestroyed`, activity pause/resume, and context loss without using stale `ANativeWindow` or `EGLSurface` handles.

A resize must update the framebuffer dimensions visible to Minecraft and the GLES viewport without recreating the whole Java client unnecessarily.

### 4.4.6 — Real-client smoke test

The milestone is complete only when a supported Minecraft version reaches the real main menu and continuously swaps frames on the Android surface.

The test must additionally cover:

1. launch → main menu
2. touch input
3. hardware keyboard input when available
4. resize/rotation
5. pause → resume
6. surface destroy → recreate
7. clean Minecraft exit

## Current gap

The existing bridge already creates an ES 3 EGL context and exposes a hand-written GLFW ABI, while the Android view forwards input into a native queue. However, the current event pump drains the queue without invoking callbacks, and the initial EGL test only clears/swaps once. The Java launcher also needs the runtime/native LWJGL path to be verified against an actual Minecraft client.

Therefore Phase 4.4 must be treated as an integration milestone, not a UI milestone.

## Definition of done

- A supported Minecraft Java version displays its real main menu in `RendererSurfaceView`.
- Frames continuously swap through the live EGL surface.
- Minecraft framebuffer size tracks Android surface size.
- Android touch, keyboard, character, scroll, and focus events reach Minecraft through GLFW callbacks.
- Surface recreation does not leave stale native handles or leaked references.
- Logcat identifies the failing layer when Java, LWJGL, GLFW, EGL, or Android surface setup fails.
