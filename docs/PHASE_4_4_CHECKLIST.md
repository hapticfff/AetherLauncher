# Phase 4.4 Implementation Checklist

- [ ] Confirm supported Minecraft version and exact LWJGL build/native ABI.
- [ ] Confirm the Android LWJGL runtime artifact contains the GLFW/OpenGL native layer required by that build.
- [ ] Define the GLFW callback typedefs and implement callback delivery from `glfwPollEvents()`.
- [ ] Make Android touch events produce stable cursor/button events, including multi-touch policy.
- [ ] Map Android key codes/scancodes/modifiers to the GLFW key model used by Minecraft.
- [ ] Implement character input without relying on `onKeyMultiple()` only.
- [ ] Persist one EGL window surface for the GLFW window instead of the attach-time clear/swap test surface.
- [ ] Make `glfwSwapBuffers()` validate the live EGL surface and report EGL errors.
- [ ] Implement resize propagation and framebuffer-size callbacks.
- [ ] Handle Android surface destruction/recreation without stale `ANativeWindow` or `EGLSurface` handles.
- [ ] Verify pause/resume and focus transitions.
- [ ] Verify LWJGL native library loading order and `java.library.path` for arm64-v8a.
- [ ] Launch a real supported Minecraft client through the bridge.
- [ ] Capture GL/EGL/LWJGL/GLFW failure stage in logcat.
- [ ] Add an automated/native smoke test for the bridge lifecycle where practical.
- [ ] Add a device smoke test proving the real Minecraft main menu renders and swaps continuously.
