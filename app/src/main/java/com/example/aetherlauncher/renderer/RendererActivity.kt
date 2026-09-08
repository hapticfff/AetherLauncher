package com.example.aetherlauncher.renderer

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** Hosts the Android Surface used by Minecraft/LWJGL and the Pojav GLFW bridge. */
class RendererActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val renderer = RendererBackend.fromLabel(intent.getStringExtra(EXTRA_RENDERER))
        val view = RendererSurfaceView(this, renderer)
        setContentView(FrameLayout(this).apply { addView(view) })
    }

    companion object {
        const val EXTRA_RENDERER = "renderer"
    }
}
