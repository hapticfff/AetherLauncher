package com.example.aetherlauncher.renderer

import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native Android graphics bridge used by the Phase 4 renderer surface. */
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
}
