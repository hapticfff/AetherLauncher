package com.example.aetherlauncher.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Runs the Android Java runtime through its JLI entry point instead of execve(). */
class NativeJavaProcess(
    runtimeDirectory: String,
    arguments: List<String>,
    renderer: String = "Auto"
) : Process() {
    companion object {
        init {
            System.loadLibrary("aetherlauncher")
        }
    }

    private val result = AtomicInteger(Int.MIN_VALUE)
    private val worker = thread(name = "Aether-Java", start = true) {
        val code = nativeLaunch(runtimeDirectory, arguments.toTypedArray(), renderer)
        result.set(code)
    }

    private external fun nativeLaunch(
        runtimeDirectory: String,
        arguments: Array<String>,
        renderer: String
    ): Int

    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int {
        worker.join()
        return result.get()
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        worker.join(unit.toMillis(timeout))
        return !worker.isAlive
    }

    override fun exitValue(): Int {
        if (worker.isAlive) throw IllegalThreadStateException("Minecraft is still running")
        return result.get()
    }

    override fun destroy() {
        // JLI owns the JVM lifecycle; forced termination is intentionally not attempted here.
    }

    override fun destroyForcibly(): Process {
        destroy()
        return this
    }

    override fun isAlive(): Boolean = worker.isAlive
}
