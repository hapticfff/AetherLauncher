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
                installer.install(version) { progress ->
                    onProgress(progress.stage)
                }.getOrThrow()
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

            onProgress("Starting Minecraft")
            launchDemo(versionId, metadata, runtime.javaExecutable, runtime.directory)
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
        val classpath = buildList {
            add(versionJar.absolutePath)
            libraries.walkTopDown()
                .filter { it.isFile && it.extension.equals("jar", true) }
                .forEach { add(it.absolutePath) }
        }.joinToString(File.pathSeparator)

        val command = mutableListOf<String>()
        command += javaExecutable
        command += "-Xmx2G"
        command += "-Djava.home=$runtimeDirectory"
        command += "-Duser.home=${gameRoot.absolutePath}"
        command += "-Djava.library.path=${File(context.applicationInfo.nativeLibraryDir).absolutePath}"
        command += "-cp"
        command += classpath
        command += metadata.mainClass
        command += "--username"
        command += "AetherDemo"
        command += "--version"
        command += versionId
        command += "--gameDir"
        command += gameRoot.absolutePath
        command += "--assetsDir"
        command += File(gameRoot, "assets").absolutePath
        metadata.assetIndex?.let {
            command += "--assetIndex"
            command += it
        }
        command += "--uuid"
        command += "00000000-0000-0000-0000-000000000000"
        command += "--accessToken"
        command += ""
        command += "--userType"
        command += "legacy"
        command += "--versionType"
        command += "release"
        command += "--demo"

        return ProcessBuilder(command)
            .directory(gameRoot)
            .redirectErrorStream(true)
            .apply {
                environment()["JAVA_HOME"] = runtimeDirectory
                environment()["PATH"] = "$runtimeDirectory/bin:${environment()["PATH"].orEmpty()}"
                environment()["LD_LIBRARY_PATH"] = "$runtimeDirectory/lib:${context.applicationInfo.nativeLibraryDir}:${environment()["LD_LIBRARY_PATH"].orEmpty()}"
            }
            .start()
    }
}
