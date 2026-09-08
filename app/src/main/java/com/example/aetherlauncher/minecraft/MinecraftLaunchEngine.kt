package com.example.aetherlauncher.minecraft

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.aetherlauncher.renderer.RendererActivity
import com.example.aetherlauncher.renderer.RendererBackend
import com.example.aetherlauncher.renderer.RendererManager
import com.example.aetherlauncher.renderer.RendererNative
import com.example.aetherlauncher.runtime.AndroidRuntimeCatalog
import com.example.aetherlauncher.runtime.JavaRuntimeManager
import com.example.aetherlauncher.runtime.NativeJavaProcess
import com.example.aetherlauncher.ui.DownloadProgressOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MinecraftLaunchEngine(private val context: Context) {
    private val repository = MinecraftRepository()
    private val installer = MinecraftInstaller(context)
    private val resolver = MinecraftJavaResolver()
    private val runtimeManager = JavaRuntimeManager(context)
    private val progressOverlay = (context as? Activity)?.let { DownloadProgressOverlay(it) }

    init {
        // Give Android a smooth UI hint. The platform may choose a higher supported refresh rate.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context as? Activity)?.window?.let { window ->
                window.attributes = window.attributes.apply { preferredRefreshRate = 60f }
            }
        }
    }

    suspend fun installAndLaunchDemo(
        versionId: String,
        renderer: RendererBackend = RendererBackend.AUTO,
        onProgress: (String) -> Unit = {}
    ): Result<Process> = withContext(Dispatchers.IO) {
        var lastUiUpdate = 0L
        var lastStage = ""

        fun report(stage: String, force: Boolean = false) {
            val now = android.os.SystemClock.uptimeMillis()
            if (!force && stage == lastStage && now - lastUiUpdate < 120L) return
            lastUiUpdate = now
            lastStage = stage
            onProgress(stage)
        }

        progressOverlay?.show("Preparing Minecraft $versionId")
        runCatching {
            val resolvedRenderer = RendererManager.resolve(context, renderer)
            report("Renderer: ${resolvedRenderer.label}", force = true)
            report("Checking Minecraft $versionId", force = true)
            val manifest = repository.fetchVersionManifest().getOrThrow()
            val version = manifest.versions.firstOrNull { it.id == versionId }
                ?: error("Minecraft version $versionId was not found in the official manifest")

            val metadata = resolver.resolve(version).getOrThrow()
            report("Java ${metadata.javaMajorVersion} required", force = true)

            if (!installer.isInstalled(versionId)) {
                report("Installing Minecraft $versionId", force = true)
                installer.install(version) { progress ->
                    progressOverlay?.update(
                        progress.stage,
                        progress.completedFiles,
                        progress.totalFiles,
                        progress.downloadedBytes,
                        progress.totalBytes
                    )
                    report(progress.stage)
                }.getOrThrow()
            } else {
                report("Minecraft $versionId is already installed", force = true)
            }

            var runtime = runtimeManager.findInstalled(metadata.javaMajorVersion)
            if (runtime == null) {
                val packageInfo = AndroidRuntimeCatalog.packageFor(
                    metadata.javaMajorVersion,
                    runtimeManager.supportedArchitecture()
                ) ?: error("No Android-compatible Java ${metadata.javaMajorVersion} runtime is available for this device")
                report("Downloading Android Java ${metadata.javaMajorVersion}", force = true)
                runtime = runtimeManager.installFromTarXz(
                    metadata.javaMajorVersion,
                    packageInfo.url,
                    packageInfo.sha256
                ) { progress ->
                    progressOverlay?.show(progress.stage, progress.downloadedBytes, progress.totalBytes)
                    report(progress.stage)
                }.getOrThrow()
            }

            check(resolvedRenderer == RendererBackend.OPENGL) {
                "Phase 4.3 Minecraft GLFW bridge currently requires the OpenGL renderer"
            }

            report("Preparing ${resolvedRenderer.label} renderer", force = true)
            RendererNative.prepareSurface()
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(context, RendererActivity::class.java).apply {
                        putExtra(RendererActivity.EXTRA_RENDERER, resolvedRenderer.label)
                    }
                )
            }
            check(RendererNative.awaitSurface(10_000L)) {
                "Timed out waiting for the Android Minecraft renderer surface"
            }

            val process = launchDemo(versionId, metadata, runtime.directory, resolvedRenderer)

            Thread.sleep(1_500)
            if (!process.isAlive) {
                error("Minecraft exited immediately (code ${process.exitValue()}). Check Android logcat for the native Java launcher output.")
            }

            report("Minecraft process started • ${resolvedRenderer.label}", force = true)
            progressOverlay?.hide()
            process
        }.onFailure {
            progressOverlay?.hide()
        }
    }

    private fun launchDemo(
        versionId: String,
        metadata: MinecraftLaunchMetadata,
        runtimeDirectory: String,
        renderer: RendererBackend
    ): Process {
        val gameRoot = installer.installationDirectory()
        val versionJar = File(gameRoot, "versions/$versionId/$versionId.jar")
        val libraries = File(gameRoot, "libraries")
        val nativesDirectory = File(gameRoot, "natives/$versionId").apply { mkdirs() }

        require(versionJar.isFile) { "Minecraft client JAR is missing: ${versionJar.absolutePath}" }

        val classpath = buildList {
            add(versionJar.absolutePath)
            libraries.walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension.equals("jar", true) &&
                        !it.name.contains("natives-", ignoreCase = true)
                }
                .forEach { add(it.absolutePath) }
        }.joinToString(File.pathSeparator)

        val values = mapOf(
            "auth_player_name" to "AetherDemo",
            "version_name" to versionId,
            "game_directory" to gameRoot.absolutePath,
            "assets_root" to File(gameRoot, "assets").absolutePath,
            "assets_index_name" to (metadata.assetIndex ?: ""),
            "auth_uuid" to "00000000-0000-0000-0000-000000000000",
            "auth_access_token" to "",
            "clientid" to "",
            "auth_xuid" to "",
            "user_type" to "legacy",
            "version_type" to "release",
            "natives_directory" to nativesDirectory.absolutePath,
            "launcher_name" to "Aether Launcher",
            "launcher_version" to "0.4.0",
            "classpath" to classpath
        )

        fun resolve(value: String): String = values.entries.fold(value) { current, (key, replacement) ->
            current.replace("\${$key}", replacement)
        }

        val command = mutableListOf<String>()
        val resolvedJvmArguments = metadata.jvmArguments
            .map(::resolve)
            .filter { it.isNotBlank() }
        command += resolvedJvmArguments
        command += "-Djava.library.path=${context.applicationInfo.nativeLibraryDir}"
        command += "-Dorg.lwjgl.glfw.libname=aetherlauncher"

        if (resolvedJvmArguments.none { it == "-cp" || it == "-classpath" }) {
            command += "-cp"
            command += classpath
        }

        command += metadata.mainClass
        val gameArguments = metadata.gameArguments.map(::resolve).toMutableList()
        if (gameArguments.isEmpty()) {
            gameArguments += "--username"
            gameArguments += "AetherDemo"
            gameArguments += "--version"
            gameArguments += versionId
            gameArguments += "--gameDir"
            gameArguments += gameRoot.absolutePath
            gameArguments += "--assetsDir"
            gameArguments += File(gameRoot, "assets").absolutePath
            gameArguments += "--assetIndex"
            gameArguments += (metadata.assetIndex ?: "")
            gameArguments += "--uuid"
            gameArguments += "00000000-0000-0000-0000-000000000000"
            gameArguments += "--accessToken"
            gameArguments += ""
            gameArguments += "--userType"
            gameArguments += "legacy"
            gameArguments += "--versionType"
            gameArguments += "release"
        }
        if ("--demo" !in gameArguments) gameArguments += "--demo"
        command += gameArguments

        return NativeJavaProcess(runtimeDirectory, command, renderer.label)
    }
}
