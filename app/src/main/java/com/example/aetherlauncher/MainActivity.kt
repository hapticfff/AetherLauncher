package com.example.aetherlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aetherlauncher.account.AccountType
import com.example.aetherlauncher.account.LauncherAccountStore
import com.example.aetherlauncher.instance.InstanceStore
import com.example.aetherlauncher.instance.LauncherInstance
import com.example.aetherlauncher.minecraft.MinecraftLaunchEngine
import com.example.aetherlauncher.renderer.RendererBackend
import com.example.aetherlauncher.renderer.RendererManager
import com.example.aetherlauncher.renderer.RendererSettingsStore
import kotlinx.coroutines.launch

private val Bg = Color(0xFF08090C)
private val CardBg = Color(0xFF111318)
private val SelectedBg = Color(0xFF1B1728)
private val Accent = Color(0xFF7C4DFF)
private val PrimaryText = Color(0xFFF5F5F5)
private val MutedText = Color(0xFF9296A1)

private enum class Page { HOME, INSTANCES, MODS, SETTINGS, LOGS }
private enum class SettingsPage { GENERAL, LAUNCHER, JAVA, RENDERER, CONTROLS, PERFORMANCE, STORAGE, LOGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { AetherTheme { LauncherRoot() } }
    }
}

@Composable
private fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = CardBg,
            primary = Accent,
            onPrimary = Color.White,
            onBackground = PrimaryText,
            onSurface = PrimaryText
        ),
        content = content
    )
}

@Composable
private fun LauncherRoot() {
    val context = LocalContext.current
    val instanceStore = remember { InstanceStore(context) }
    val accountStore = remember { LauncherAccountStore(context) }
    val rendererStore = remember { RendererSettingsStore(context) }
    val launchEngine = remember { MinecraftLaunchEngine(context) }
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(Page.HOME) }
    var settingsPage by remember { mutableStateOf(SettingsPage.GENERAL) }
    var instances by remember { mutableStateOf(instanceStore.list()) }
    var selected by remember { mutableStateOf(instanceStore.ensureDefault()) }
    var account by remember { mutableStateOf(accountStore.selected()) }
    var renderer by remember { mutableStateOf(rendererStore.get()) }
    var launching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }

    LaunchedEffect(Unit) {
        LauncherLog.add("Aether Launcher UI initialized")
        if (accountStore.accounts().isEmpty()) accountStore.addOfflineDemo("Offline account")
        account = accountStore.selected() ?: accountStore.accounts().firstOrNull()
    }

    BackHandler(enabled = page != Page.HOME) {
        if (page == Page.SETTINGS && settingsPage != SettingsPage.GENERAL) {
            settingsPage = SettingsPage.GENERAL
        } else {
            page = Page.HOME
        }
    }

    fun saveInstance(value: LauncherInstance) {
        selected = value
        instanceStore.save(value)
        instances = instanceStore.list()
    }

    fun launch() {
        if (launching) return
        if (account?.type != AccountType.OFFLINE_DEMO) {
            status = "Select a supported local test account"
            LauncherLog.add("Launch rejected: unsupported account", "WARN")
            return
        }
        launching = true
        status = "Preparing Minecraft ${selected.minecraftVersion}…"
        LauncherLog.add("Launch requested: ${selected.name}")
        scope.launch {
            val backend = RendererBackend.fromLabel(selected.renderer)
            launchEngine.installAndLaunchDemo(selected.minecraftVersion, backend) { step ->
                status = step
                LauncherLog.add(step)
            }.onSuccess {
                status = "Minecraft process started"
                launching = false
            }.onFailure {
                status = it.message ?: "Launch failed"
                launching = false
                LauncherLog.add(status, "ERROR")
            }
        }
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                NavigationBarItem(page == Page.HOME, { page = Page.HOME }, { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Play", fontSize = 10.sp) })
                NavigationBarItem(page == Page.INSTANCES, { page = Page.INSTANCES }, { Icon(Icons.Default.ViewList, null) }, label = { Text("Instances", fontSize = 10.sp) })
                NavigationBarItem(page == Page.MODS, { page = Page.MODS }, { Icon(Icons.Default.Extension, null) }, label = { Text("Mods", fontSize = 10.sp) })
                NavigationBarItem(page == Page.SETTINGS, { page = Page.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings", fontSize = 10.sp) })
                NavigationBarItem(page == Page.LOGS, { page = Page.LOGS }, { Icon(Icons.Default.Info, null) }, label = { Text("Logs", fontSize = 10.sp) })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.HOME -> HomePage(selected, account?.name ?: "No account", launching, status, ::launch) { target -> page = target }
                Page.INSTANCES -> InstancesPage(instances, selected, ::saveInstance, {
                    val created = LauncherInstance(
                        java.util.UUID.randomUUID().toString(),
                        "New Instance",
                        selected.minecraftVersion,
                        selected.loader,
                        selected.javaMajor,
                        selected.renderer,
                        selected.maxRamMb
                    )
                    instanceStore.save(created)
                    instances = instanceStore.list()
                    LauncherLog.add("Created instance: ${created.name}")
                }, { id ->
                    instanceStore.delete(id)
                    instances = instanceStore.list()
                    if (selected.id == id) selected = instanceStore.ensureDefault()
                })
                Page.MODS -> ModsPage(selected.minecraftVersion, selected.loader)
                Page.SETTINGS -> SettingsRoot(settingsPage, { settingsPage = it }, renderer, { value ->
                    renderer = value
                    rendererStore.set(value)
                    saveInstance(selected.copy(renderer = value.label))
                }, selected, ::saveInstance)
                Page.LOGS -> LogsPage()
            }
        }
    }
}

@Composable
private fun HomePage(instance: LauncherInstance, account: String, launching: Boolean, status: String, onPlay: () -> Unit, navigate: (Page) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Text("AETHER", color = PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("MINECRAFT JAVA LAUNCHER", color = MutedText, fontSize = 9.sp, letterSpacing = 1.4.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(instance.name, color = PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Minecraft ${instance.minecraftVersion} • ${instance.loader}", color = MutedText, fontSize = 11.sp)
                    Text("Java ${instance.javaMajor} • ${instance.maxRamMb} MB • ${instance.renderer}", color = MutedText, fontSize = 10.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onPlay, enabled = !launching, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (launching) "LAUNCHING" else "PLAY", fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = MutedText, fontSize = 10.sp)
                }
            }
        }
        item { Text("Account: $account", color = MutedText, fontSize = 10.sp) }
        item { QuickActions(navigate) }
    }
}

@Composable
private fun QuickActions(navigate: (Page) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionRow(Icons.Default.ViewList, "Instances", "Manage Minecraft profiles") { navigate(Page.INSTANCES) }
        ActionRow(Icons.Default.Extension, "Mods", "Browse compatible mods") { navigate(Page.MODS) }
        ActionRow(Icons.Default.Settings, "Settings", "Java, renderer, controls and storage") { navigate(Page.SETTINGS) }
        ActionRow(Icons.Default.Info, "Logs", "Launcher diagnostics") { navigate(Page.LOGS) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = MutedText, fontSize = 9.sp)
        }
    }
}

@Composable
private fun InstancesPage(list: List<LauncherInstance>, selected: LauncherInstance, select: (LauncherInstance) -> Unit, add: () -> Unit, delete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("INSTANCES", "Independent Minecraft profiles")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = add, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("NEW")
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(list, key = { it.id }) { instance ->
                Row(
                    Modifier.fillMaxWidth().background(if (instance.id == selected.id) SelectedBg else CardBg, RoundedCornerShape(16.dp)).clickable { select(instance) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = Accent)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(instance.name, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${instance.minecraftVersion} • ${instance.loader} • Java ${instance.javaMajor}", color = MutedText, fontSize = 9.sp)
                        Text("${instance.renderer} • ${instance.maxRamMb} MB RAM", color = MutedText, fontSize = 9.sp)
                    }
                    IconButton(onClick = { delete(instance.id) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MutedText) }
                }
            }
        }
    }
}

@Composable
private fun ModsPage(version: String, loader: String) {
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("MODS", "Mod management")
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Modrinth integration", color = PrimaryText, fontWeight = FontWeight.Bold)
                Text("Compatible mods for Minecraft $version / $loader can be prepared here.", color = MutedText, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Text("Use the launcher runtime and instance storage for installed mods.", color = MutedText, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SettingsRoot(page: SettingsPage, select: (SettingsPage) -> Unit, renderer: RendererBackend, setRenderer: (RendererBackend) -> Unit, instance: LauncherInstance, save: (LauncherInstance) -> Unit) {
    if (page == SettingsPage.GENERAL) {
        SettingsMenu(select)
    } else {
        when (page) {
            SettingsPage.LAUNCHER -> LauncherSettings(instance)
            SettingsPage.JAVA -> JavaSettings(instance, save)
            SettingsPage.RENDERER -> RendererSettings(renderer, setRenderer)
            SettingsPage.CONTROLS -> ControlsSettings()
            SettingsPage.PERFORMANCE -> PerformanceSettings(instance, save)
            SettingsPage.STORAGE -> StorageSettings()
            SettingsPage.LOGS -> LogsPage()
            SettingsPage.GENERAL -> Unit
        }
    }
}

@Composable
private fun SettingsMenu(select: (SettingsPage) -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("SETTINGS", "Aether launcher configuration")
        Spacer(Modifier.height(8.dp))
        SettingRow(Icons.Default.Settings, "Launcher", "Downloads, verification and startup") { select(SettingsPage.LAUNCHER) }
        SettingRow(Icons.Default.Terminal, "Java runtime", "Runtime, memory and JVM options") { select(SettingsPage.JAVA) }
        SettingRow(Icons.Default.Bolt, "Video / renderer", "Renderer, surface and synchronization") { select(SettingsPage.RENDERER) }
        SettingRow(Icons.Default.Gamepad, "Controls", "Touch, mouse, gestures and controller") { select(SettingsPage.CONTROLS) }
        SettingRow(Icons.Default.Speed, "Performance", "FPS, resolution and performance mode") { select(SettingsPage.PERFORMANCE) }
        SettingRow(Icons.Default.Storage, "Storage", "Game directory, downloads and cleanup") { select(SettingsPage.STORAGE) }
        SettingRow(Icons.Default.Info, "Logs", "Diagnostics and launcher log") { select(SettingsPage.LOGS) }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = MutedText, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LauncherSettings(instance: LauncherInstance) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_launcher_settings", 0) }
    var automatic by remember { mutableStateOf(prefs.getBoolean("auto_download", true)) }
    var verify by remember { mutableStateOf(prefs.getBoolean("verify", true)) }
    var skipHash by remember { mutableStateOf(prefs.getBoolean("skip_hash", false)) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("LAUNCHER", "General launcher behavior")
        SwitchSetting("Automatic downloads", "Prepare required files before launch", automatic) { automatic = it; prefs.edit().putBoolean("auto_download", it).apply() }
        SwitchSetting("Verify downloads", "Validate downloaded files before launch", verify) { verify = it; prefs.edit().putBoolean("verify", it).apply() }
        SwitchSetting("Skip verification warnings", "Do not stop launch for non-critical verification warnings", skipHash) { skipHash = it; prefs.edit().putBoolean("skip_hash", it).apply() }
        InfoCard("Selected instance", instance.name)
    }
}

@Composable
private fun JavaSettings(instance: LauncherInstance, save: (LauncherInstance) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_java_settings", 0) }
    var ram by remember { mutableFloatStateOf(instance.maxRamMb.toFloat()) }
    var sandbox by remember { mutableStateOf(prefs.getBoolean("sandbox", false)) }
    var args by remember { mutableStateOf(instance.jvmArgs) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("JAVA RUNTIME", "Java ${instance.javaMajor} • per-instance configuration")
        Spacer(Modifier.height(10.dp))
        InfoCard("Java runtime", "Java ${instance.javaMajor}")
        Spacer(Modifier.height(8.dp))
        Text("RAM limit: ${ram.toInt()} MB", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Slider(value = ram, onValueChange = { ram = it }, onValueChangeFinished = { save(instance.copy(maxRamMb = ram.toInt())) }, valueRange = 768f..8192f)
        Spacer(Modifier.height(4.dp))
        Text("JVM arguments", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(args.ifBlank { "No custom JVM arguments" }, color = MutedText, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { args = if (args.isBlank()) "-XX:+UseG1GC" else ""; save(instance.copy(jvmArgs = args)) }) { Text(if (args.isBlank()) "ADD SAFE DEFAULT" else "CLEAR JVM ARGS") }
        Spacer(Modifier.height(4.dp))
        SwitchSetting("Sandbox JAR execution", "Allow launching an external Java JAR from the launcher", sandbox) { sandbox = it; prefs.edit().putBoolean("sandbox", it).apply() }
    }
}

@Composable
private fun RendererSettings(current: RendererBackend, set: (RendererBackend) -> Unit) {
    val context = LocalContext.current
    val availability = remember { RendererManager.availability(context) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("VIDEO / RENDERER", "Rendering options")
        Spacer(Modifier.height(10.dp))
        Text("Renderer", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("The selected backend is stored with the current instance.", color = MutedText, fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
        for (backend in RendererBackend.entries) {
            val available = when (backend) {
                RendererBackend.AUTO -> true
                RendererBackend.OPENGL -> availability.openGl
                RendererBackend.VULKAN -> availability.vulkan
            }
            ChoiceRow(backend.label, when (backend) {
                RendererBackend.AUTO -> "Let Aether choose the available backend"
                RendererBackend.OPENGL -> "OpenGL ES path"
                RendererBackend.VULKAN -> "Vulkan-capable path"
            }, current == backend, available) { if (available) set(backend) }
        }
        Spacer(Modifier.height(12.dp))
        RendererExtraSettings()
    }
}

@Composable
private fun RendererExtraSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_video_settings", 0) }
    var notch by remember { mutableStateOf(prefs.getBoolean("ignore_notch", false)) }
    var sustained by remember { mutableStateOf(prefs.getBoolean("sustained", false)) }
    var alternateSurface by remember { mutableStateOf(prefs.getBoolean("alternate_surface", false)) }
    var vsync by remember { mutableStateOf(prefs.getBoolean("vsync", true)) }
    var zinkVsync by remember { mutableStateOf(prefs.getBoolean("zink_vsync", true)) }
    SwitchSetting("Ignore display cutout", "Use the full display area when supported", notch) { notch = it; prefs.edit().putBoolean("ignore_notch", it).apply() }
    SwitchSetting("Sustained performance", "Request a sustained-performance mode from Android", sustained) { sustained = it; prefs.edit().putBoolean("sustained", it).apply() }
    SwitchSetting("Alternate surface rendering", "Use the alternate Android surface path when supported", alternateSurface) { alternateSurface = it; prefs.edit().putBoolean("alternate_surface", it).apply() }
    SwitchSetting("Force VSync", "Synchronize frame presentation with display refresh", vsync) { vsync = it; prefs.edit().putBoolean("vsync", it).apply() }
    SwitchSetting("VSync with Vulkan/Zink", "Apply the VSync preference to the Vulkan translation path", zinkVsync) { zinkVsync = it; prefs.edit().putBoolean("zink_vsync", it).apply() }
}

@Composable
private fun ControlsSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_controls", 0) }
    var touch by remember { mutableStateOf(prefs.getBoolean("touch", true)) }
    var gamepad by remember { mutableStateOf(prefs.getBoolean("gamepad", true)) }
    var virtualMouse by remember { mutableStateOf(prefs.getBoolean("virtual_mouse", true)) }
    var gyro by remember { mutableStateOf(prefs.getBoolean("gyro", false)) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("CONTROLS", "Touch, mouse and controller")
        SwitchSetting("Touch controls", "On-screen controls for Minecraft", touch) { touch = it; prefs.edit().putBoolean("touch", it).apply() }
        SwitchSetting("Gamepad", "Android controller input", gamepad) { gamepad = it; prefs.edit().putBoolean("gamepad", it).apply() }
        SwitchSetting("Virtual mouse", "Use touch input as a desktop-style mouse", virtualMouse) { virtualMouse = it; prefs.edit().putBoolean("virtual_mouse", it).apply() }
        SwitchSetting("Gyroscope", "Use device motion where supported by controls", gyro) { gyro = it; prefs.edit().putBoolean("gyro", it).apply() }
        InfoCard("Control customization", "Per-game control layout and button mapping will use the selected instance profile.")
    }
}

@Composable
private fun PerformanceSettings(instance: LauncherInstance, save: (LauncherInstance) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_performance", 0) }
    var fps by remember { mutableFloatStateOf(prefs.getFloat("fps", 60f)) }
    var resolution by remember { mutableFloatStateOf(prefs.getFloat("resolution", 100f)) }
    var lowPower by remember { mutableStateOf(prefs.getBoolean("low_power", false)) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("PERFORMANCE", "FPS, resolution and device performance")
        Spacer(Modifier.height(10.dp))
        Text("FPS limit: ${fps.toInt()}", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Slider(value = fps, onValueChange = { fps = it }, onValueChangeFinished = { prefs.edit().putFloat("fps", fps).apply() }, valueRange = 30f..240f)
        Text("Resolution scale: ${resolution.toInt()}%", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Slider(value = resolution, onValueChange = { resolution = it }, onValueChangeFinished = { prefs.edit().putFloat("resolution", resolution).apply() }, valueRange = 50f..100f)
        SwitchSetting("Power-efficient mode", "Prefer lower sustained load when playing", lowPower) { lowPower = it; prefs.edit().putBoolean("low_power", it).apply() }
        InfoCard("Instance", "${instance.name} • ${instance.minecraftVersion} • ${instance.loader}")
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { save(instance) }) { Text("SAVE PROFILE") }
    }
}

@Composable
private fun StorageSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aether_storage_settings", 0) }
    var autoClean by remember { mutableStateOf(prefs.getBoolean("auto_clean", false)) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("STORAGE", "Game files, runtimes and downloads")
        InfoCard("Game directory", "Aether app-private storage / Minecraft data")
        InfoCard("Runtime cache", "Downloaded Java and LWJGL runtime files are reused when possible")
        SwitchSetting("Automatic cache cleanup", "Remove unused cached downloads when storage is low", autoClean) { autoClean = it; prefs.edit().putBoolean("auto_clean", it).apply() }
        OutlinedButton(onClick = { LauncherLog.add("Storage cleanup requested") }) { Text("CLEAN UNUSED CACHE") }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) SelectedBg else CardBg, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) PrimaryText else MutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = MutedText, fontSize = 9.sp)
        }
        Text(if (selected) "✓" else if (!enabled) "—" else "", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Text(title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(value, color = MutedText, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = MutedText, fontSize = 9.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LogsPage() {
    var refresh by remember { mutableIntStateOf(0) }
    val entries = remember(refresh) { LauncherLog.snapshot() }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        PageTitle("LOGS", "Aether diagnostics")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { LauncherLog.add("Manual log refresh"); refresh++ }) { Text("REFRESH") }
            Button(onClick = { LauncherLog.clear(); refresh++ }) { Text("CLEAR") }
        }
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Text("No launcher logs yet.", color = MutedText, fontSize = 12.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries) { line -> Text(line, color = PrimaryText, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(subtitle, color = MutedText, fontSize = 10.sp)
    }
}
