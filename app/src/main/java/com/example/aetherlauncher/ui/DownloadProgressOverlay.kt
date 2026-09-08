package com.example.aetherlauncher.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

/** Lightweight bottom download surface kept outside the Compose recomposition tree. */
class DownloadProgressOverlay(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var title: TextView? = null
    private var detail: TextView? = null
    private var progress: ProgressBar? = null
    private var lastUpdateAt = 0L
    private var lastStage = ""

    fun show(stage: String, downloadedBytes: Long = 0L, totalBytes: Long = 0L) {
        postUpdate(stage, 0, 0, downloadedBytes, totalBytes, force = true)
    }

    fun update(
        stage: String,
        completedFiles: Int,
        totalFiles: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        // Do not redraw the Android window for every asset; large asset indexes can contain thousands of files.
        postUpdate(stage, completedFiles, totalFiles, downloadedBytes, totalBytes, force = false)
    }

    fun hide() {
        main.post {
            popup?.dismiss()
            popup = null
            title = null
            detail = null
            progress = null
            lastStage = ""
        }
    }

    private fun postUpdate(
        stage: String,
        completedFiles: Int,
        totalFiles: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        force: Boolean
    ) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && stage == lastStage && now - lastUpdateAt < 100L) return
        lastUpdateAt = now
        lastStage = stage
        main.post {
            ensureWindow()
            title?.text = stage
            val percentage = when {
                totalBytes > 0L -> ((downloadedBytes.toDouble() / totalBytes) * 100.0).roundToInt().coerceIn(0, 100)
                totalFiles > 0 -> ((completedFiles.toDouble() / totalFiles) * 100.0).roundToInt().coerceIn(0, 100)
                else -> 0
            }
            detail?.text = when {
                totalBytes > 0L -> "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}  •  $percentage%"
                totalFiles > 0 -> "$completedFiles / $totalFiles files  •  $percentage%"
                else -> "Preparing…"
            }
            progress?.isIndeterminate = totalBytes <= 0L && totalFiles <= 0
            if (!progress!!.isIndeterminate) progress?.progress = percentage
        }
    }

    private fun ensureWindow() {
        if (popup?.isShowing == true) return
        if (activity.isFinishing || activity.isDestroyed) return

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(AndroidColor.TRANSPARENT)
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(AndroidColor.rgb(17, 19, 24))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), AndroidColor.argb(28, 255, 255, 255))
            }
        }
        title = TextView(activity).apply {
            setTextColor(AndroidColor.rgb(245, 245, 245))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        detail = TextView(activity).apply {
            setTextColor(AndroidColor.rgb(146, 150, 161))
            textSize = 10f
            setPadding(0, dp(3), 0, dp(8))
        }
        progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            progressDrawable.setTint(AndroidColor.rgb(124, 77, 255))
        }
        card.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        popup = PopupWindow(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(AndroidColor.TRANSPARENT))
            isOutsideTouchable = false
            isTouchable = false
            elevation = dp(10).toFloat()
        }
        popup?.showAtLocation(activity.window.decorView, Gravity.BOTTOM, 0, 0)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
