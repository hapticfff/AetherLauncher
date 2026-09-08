package com.example.aetherlauncher.minecraft

import android.content.Context
import com.example.aetherlauncher.runtime.AndroidRuntimeCatalog
import com.example.aetherlauncher.runtime.JavaRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MinecraftLaunchEngine(private val context: Context) {
    private val repository = MinecraftRepository()
    private val installer = MinecraftInstaller(context)
    private val resolver = MinecraftJavaResolver()
    private val runtimeManager = JavaRuntimeManager(context)

    suspend fun installAndLaunchDemo(
        versionId: String,
        onProgress: (String) -> Unit = {}
    ): Result<Process> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Checking Minecraft $versionId")
            val manifest = repository.fetchVersionManifest().getOrThrow()
            val version = manifest.versions.firstOrNull { it.id == versionId }
                ?: error("Minecraft version $versionId was not found in the official manifest")

            val metadata = resolver.resolve(version).getOrThrow()
            onProgress("Java ${metadata.javaMajorVersion} required")

            if (!installer.isInstalled(versionId)) {
                onProgress("Installing Minecraft $versionId")
                installer.install(version) { progress -> onProgress(progress.stage) }.getOrThrow()
            } else {
                onProgress("Minecraft $versionId is already installed")
            }

            var runtime = runtimeManager.findInstalled(metadata.javaMajorVersion)
            if (runtime == null) {
                val packageInfo = AndroidRuntimeCatalog.packageFor(
                    metadata.javaMajorVersion,
                    runtimeManager.supportedArchitecture()
                ) ?: error("No Android-compatible Java ${metadata.javaMajorVersion} runtime is available for this device")
                onProgress("Downloading Android Java ${metadata.javaMajorVersion}")
                runtime = runtimeManager.installFromTarXz(
                    metadata.javaMajorVersion,
                    packageInfo.url,
                    packageInfo.sha256
                ) { progress -> onProgress(progress.stage) }.getOrThrow()
            }

            onProgress("Preparing Minecraft runtime")
            val process = launchDemo(versionId, metadata, runtime.javaExecutable, runtime.directory)

            Thread.sleep(1_500)
            if (!process.isAlive) {
                val log = readLaunchLog(versionId)
                error("Minecraft exited immediately (code ${process.exitValue()}). ${log.ifBlank { "No Minecraft output was produced." }}")
            }

            onProgress("Minecraft process started")
            process
        }
    }

    private fun launchDemo(
        versionId: String,
        metadata: MinecraftLaunchMetadata,
        javaExecutable: String,
        runtimeDirectory: String
    ): Process {
        val gameRoot = installer.installationDirectory()
        val versionJar = File(gameRoot, "versions/$versionId/$versionId.jar")
        val libraries = File(gameRoot, "libraries")
        val nativesDirectory = File(gameRoot, "natives/$versionId").apply { mkdirs() }
        val logFile = File(gameRoot, "logs/latest-launch.log").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }

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
            "launcher_version" to "0.3.0",
            "classpath" to classpath
        )

        fun resolve(value: String): String = values.entries.fold(value) { current, (key, replacement) ->
            current.replace("\${$key}", replacement)
        }

        val command = mutableListOf<String>()
        command += javaExecutable

        val resolvedJvmArguments = metadata.jvmArguments
            .map(::resolve)
            .filter { it.isNotBlank() }
        command += resolvedJvmArguments

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

        val libraryPath = "${nativesDirectory.absolutePath}${File.pathSeparator}${context.applicationInfo.nativeLibraryDir}"
        val runtimeLibraryPath = listOf(
            File(runtimeDirectory, "lib/jli"),
            File(runtimeDirectory, "lib/server"),
            File(runtimeDirectory, "lib"),
            File(runtimeDirectory, "lib/jvm"),
            nativesDirectory,
            File(context.applicationInfo.nativeLibraryDir)
        ).filter { it.isDirectory }.joinToString(File.pathSeparator)

        return ProcessBuilder(command)
            .directory(gameRoot)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .apply {
                environment()["JAVA_HOME"] = runtimeDirectory
                environment()["PATH"] = "$runtimeDirectory/bin:${environment()["PATH"].orEmpty()}"
                environment()["LD_LIBRARY_PATH"] = "$runtimeLibraryPath:${environment()["LD_LIBRARY_PATH"].orEmpty()}"
                environment()["AETHER_NATIVE_LIBRARY_PATH"] = libraryPath
            }
            .start()
    }

    private fun readLaunchLog(versionId: String): String {
        val file = File(installer.installationDirectory(), "logs/latest-launch.log")
        if (!file.isFile) return ""
        val text = runCatching { file.readText() }.getOrDefault("").trim()
        if (text.isBlank()) return ""
        val tail = text.takeLast(4_000)
        return "Minecraft $versionId log: ${tail.replace('\n', ' ').replace('\r', ' ')}"
    }
}
