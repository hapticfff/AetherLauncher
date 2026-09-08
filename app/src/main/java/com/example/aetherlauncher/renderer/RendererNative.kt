package com.example.aetherlauncher.renderer

import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native Android graphics and input bridge used by the Phase 4 renderer surface. */
object RendererNative {
    init {
        System.loadLibrary("aetherlauncher")
    }

    @Volatile
    private var surfaceLatch = CountDownLatch(1)

    fun prepareSurface() {
        surfaceLatch = CountDownLatch(1)
    }

    fun awaitSurface(timeoutMs: Long): Boolean =
        surfaceLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun notifySurfaceCreated() {
        surfaceLatch.countDown()
    }

    external fun attachSurface(surface: Surface, renderer: String): Boolean
    external fun detachSurface()
    external fun rendererStatus(): String

    external fun queueTouch(action: Int, x: Float, y: Float, pointerId: Int)
    external fun queueKey(keyCode: Int, scanCode: Int, action: Int, metaState: Int)
    external fun queueChar(codePoint: Int)
    external fun queueScroll(horizontal: Float, vertical: Float)
    external fun queueFocus(focused: Boolean)
    external fun queueResize(width: Int, height: Int)
}
