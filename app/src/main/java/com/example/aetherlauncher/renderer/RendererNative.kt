package com.example.aetherlauncher.renderer

import android.view.Surface

/** Native Android graphics bridge used by the Phase 4 renderer surface. */
object RendererNative {
    init {
        System.loadLibrary("aetherlauncher")
    }

    external fun attachSurface(surface: Surface, renderer: String): Boolean
    external fun detachSurface()
    external fun rendererStatus(): String
}
