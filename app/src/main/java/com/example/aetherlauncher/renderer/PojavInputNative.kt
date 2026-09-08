package com.example.aetherlauncher.renderer

import android.view.Surface

/** Phase 4.4 bridge into Pojav's real GLFW/EGL/input ABI. */
object PojavInputNative {
    init { System.loadLibrary("aetherlauncher") }

    external fun bindSurface(surface: Surface)
    external fun unbindSurface()
    external fun sendTouch(action: Int, x: Float, y: Float, pointerId: Int)
    external fun sendKey(keyCode: Int, scanCode: Int, action: Int, metaState: Int)
    external fun sendChar(codePoint: Int)
    external fun sendScroll(horizontal: Float, vertical: Float)
    external fun sendResize(width: Int, height: Int)
}
