package com.example.aetherlauncher.renderer

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns the Android Surface and forwards input into the real Pojav/LWJGL GLFW bridge. */
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
        PojavInputNative.bindSurface(holder.surface)
        RendererNative.notifySurfaceCreated()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        PojavInputNative.unbindSurface()
        RendererNative.detachSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        PojavInputNative.sendResize(width, height)
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
                PojavInputNative.sendTouch(
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 0
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> 1
                        else -> 2
                    },
                    event.getX(index),
                    event.getY(index),
                    event.getPointerId(index)
                )
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            PojavInputNative.sendScroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        PojavInputNative.sendKey(keyCode, event.scanCode, 1, event.metaState)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        PojavInputNative.sendKey(keyCode, event.scanCode, 0, event.metaState)
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        event.characters?.forEach { character -> PojavInputNative.sendChar(character.code) }
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = false
}
