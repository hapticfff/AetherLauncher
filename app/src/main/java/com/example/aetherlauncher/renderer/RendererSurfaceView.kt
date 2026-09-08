package com.example.aetherlauncher.renderer

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns the Android Surface and forwards Android input into the native GLFW bridge. */
class RendererSurfaceView(
    context: Context,
    private val renderer: RendererBackend
) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val attached = RendererNative.attachSurface(holder.surface, renderer.label)
        if (attached) RendererNative.notifySurfaceCreated()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        RendererNative.detachSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        RendererNative.queueResize(width, height)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        RendererNative.queueFocus(hasWindowFocus)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                val pointer = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                val action = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 0
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> 1
                    else -> 2
                }
                RendererNative.queueTouch(action, x, y, pointer)
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            RendererNative.queueScroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        RendererNative.queueKey(keyCode, event.scanCode, 1, event.metaState)
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        RendererNative.queueKey(keyCode, event.scanCode, 0, event.metaState)
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        event.characters?.forEach { character ->
            RendererNative.queueChar(character.code)
        }
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = false
}
