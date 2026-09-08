package com.example.aetherlauncher.renderer

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns the Android Surface used by the native OpenGL/Vulkan backend. */
class RendererSurfaceView(
    context: Context,
    private val renderer: RendererBackend
) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val attached = RendererNative.attachSurface(holder.surface, renderer.label)
        if (attached) RendererNative.notifySurfaceCreated()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        RendererNative.detachSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
}
