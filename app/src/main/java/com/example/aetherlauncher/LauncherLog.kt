package com.example.aetherlauncher

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LauncherLog {
    private const val TAG = "AetherLauncher"
    private val entries = mutableStateListOf<String>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun add(message: String, level: String = "INFO") {
        val line = "${format.format(Date())} [$level] $message"
        synchronized(entries) {
            entries.add(line)
            if (entries.size > 500) entries.removeAt(0)
        }
        when (level) {
            "ERROR" -> Log.e(TAG, message)
            "WARN" -> Log.w(TAG, message)
            else -> Log.i(TAG, message)
        }
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
        add("Log buffer cleared")
    }
}
