package com.example.aetherlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aetherlauncher.account.AccountType
import com.example.aetherlauncher.account.LauncherAccountStore
import com.example.aetherlauncher.instance.InstanceStore
import com.example.aetherlauncher.instance.LauncherInstance
import com.example.aetherlauncher.minecraft.MinecraftLaunchEngine
import com.example.aetherlauncher.minecraft.mods.ModInstaller
import com.example.aetherlauncher.minecraft.mods.ModProject
import com.example.aetherlauncher.minecraft.mods.ModProvider
import com.example.aetherlauncher.minecraft.mods.ModrinthProvider
import com.example.aetherlauncher.renderer.RendererBackend
import com.example.aetherlauncher.renderer.RendererManager
import com.example.aetherlauncher.renderer.RendererSettingsStore
import com.example.aetherlauncher.runtime.JavaRuntimeManager
import kotlinx.coroutines.launch

private val Bg = Color(0xFF08090C)
private val Card = Color(0xFF111318)
private val Selected = Color(0xFF1B1728)
private val Accent = Color(0xFF7C4DFF)
private val TextPrimary = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9296A1)
private val Good = Color(0xFF42D392)
private enum class Page { HOME, INSTANCES, MODS, SETTINGS, LOGS }
private enum class SettingsPage { GENERAL, LAUNCHER, JAVA, RENDERER, CONTROLS, PERFORMANCE, STORAGE, LOGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { AetherTheme { FullLauncher() } }
    }
}

@Composable private fun AetherTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent, onPrimary = Color.White, onBackground = TextPrimary, onSurface = TextPrimary),
    content = content
)

@Composable private fun FullLauncher() {
    val context = LocalContext.current
    val instances = remember { InstanceStore(context) }
    val accountStore = remember { LauncherAccountStore(context) }
    val rendererStore = remember { RendererSettingsStore(context) }
    val launchEngine = remember { MinecraftLaunchEngine(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var settingsPage by remember { mutableStateOf(SettingsPage.GENERAL) }
    var list by remember { mutableStateOf(instances.list()) }
    var selected by remember { mutableStateOf(instances.ensureDefault()) }
    var accounts by remember { mutableStateOf(accountStore.accounts()) }
    var account by remember { mutableStateOf(accountStore.selected()) }
    var renderer by remember { mutableStateOf(rendererStore.get()) }
    var launching by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        LauncherLog.add("Aether Launcher UI initialized")
        if (accounts.isEmpty()) accountStore.addOfflineDemo("Offline account")
        accounts = accountStore.accounts()
        account = accountStore.selected() ?: accounts.firstOrNull()
    }
    BackHandler(enabled = page != Page.HOME) {
        if (page == Page.SETTINGS && settingsPage != SettingsPage.GENERAL) settingsPage = SettingsPage.GENERAL else page = Page.HOME
    }

    fun launch() {
        if (launching) return
        if (account?.type != AccountType.OFFLINE_DEMO) {
            error = "Select a supported local test account"
            LauncherLog.add("Launch rejected: unsupported account", "WARN")
            return
        }
        launching = true
        error = null
        progress = "Preparing ${selected.minecraftVersion} • ${selected.renderer}…"
        LauncherLog.add("Launch requested: ${selected.name} / Minecraft ${selected.minecraftVersion} / ${selected.renderer}")
        scope.launch {
            val r = RendererBackend.fromLabel(selected.renderer)
            launchEngine.installAndLaunchDemo(selected.minecraftVersion, r) { step ->
                progress = step
                LauncherLog.add(step)
            }.onSuccess {
                progress = "Minecraft process started"
                launching = false
                LauncherLog.add("Minecraft process started", "INFO")
            }.onFailure {
                error = it.message ?: "Launch failed"
                launching = false
                LauncherLog.add(error ?: "Launch failed", "ERROR")
            }
        }
    }

    Scaffold(containerColor = Bg, bottomBar = { NavigationBar(containerColor = Card) {
        NavigationBarItem(selected = page == Page.HOME, onClick = { page = Page.HOME }, icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("Play", fontSize = 10.sp) })
        NavigationBarItem(selected = page == Page.INSTANCES, onClick = { page = Page.INSTANCES }, icon = { Icon(Icons.Default.ViewList, null) }, label = { Text("Instances", fontSize = 10.sp) })
        NavigationBarItem(selected = page == Page.MODS, onClick = { page = Page.MODS }, icon = { Icon(Icons.Default.Extension, null) }, label = { Text("Mods", fontSize = 10.sp) })
        NavigationBarItem(selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings", fontSize = 10.sp) })
        NavigationBarItem(selected = page == Page.LOGS, onClick = { page = Page.LOGS }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Logs", fontSize = 10.sp) })
    } }) { pad -> Box(Modifier.padding(pad).fillMaxSize()) {
        when (page) {
            Page.HOME -> HomePage(selected, account?.name ?: "No account", renderer, launching, progress, error, { launch() }, { page = Page.INSTANCES }, { page = Page.MODS }, { page = Page.SETTINGS }, { page = Page.LOGS })
            Page.INSTANCES -> InstancesPage(list, selected, { instance -> selected = instance; page = Page.HOME }, { val n = LauncherInstance(java.util.UUID.randomUUID().toString(), "New Instance", selected.minecraftVersion, selected.loader, selected.javaMajor, selected.renderer, selected.maxRamMb); instances.save(n); list = instances.list(); LauncherLog.add("Created instance: ${n.name}") }, { id -> instances.delete(id); list = instances.list(); if (selected.id == id) selected = instances.ensureDefault(); LauncherLog.add("Deleted instance: $id") })
            Page.MODS -> ModsPage(selected.minecraftVersion, selected.loader)
            Page.SETTINGS -> SettingsRoot(settingsPage, { settingsPage = it }, renderer, { renderer = it; rendererStore.set(it); selected = selected.copy(renderer = it.label); instances.save(selected); list = instances.list(); LauncherLog.add("Renderer changed to ${it.label}") }, selected, { value -> selected = value; instances.save(value); list = instances.list(); LauncherLog.add("Instance settings saved") }, { page = Page.LOGS })
            Page.LOGS -> LogsPage()
        }
    } }
}

@Composable private fun HomePage(i: LauncherInstance, account: String, renderer: RendererBackend, launching: Boolean, progress: String, error: String?, onPlay: () -> Unit, onInstances: () -> Unit, onMods: () -> Unit, onSettings: () -> Unit, onLogs: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AetherLogo(Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column { Text("AETHER", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("MINECRAFT JAVA LAUNCHER", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp) }
                }
                Icon(Icons.Default.CloudDone, "Ready", tint = Good)
            }
        }
        item { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).background(Accent.copy(alpha = .18f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { AetherLogo(Modifier.size(48.dp)) }
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(i.name, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Minecraft ${i.minecraftVersion} • ${i.loader}", color = Muted, fontSize = 11.sp); Text("Java ${i.javaMajor} • ${i.maxRamMb} MB • ${i.renderer}", color = Muted, fontSize = 10.sp) }
            }
            Spacer(Modifier.height(16.dp)); Button(onClick = onPlay, enabled = !launching, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(17.dp)) { Icon(if (launching) Icons.Default.HourglassTop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (launching) "LAUNCHING" else "PLAY", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) }
        } }
        if (launching) item { Text(progress, Modifier.fillMaxWidth(), color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center) }
        error?.let { e -> item { Surface(Modifier.fillMaxWidth(), color = Selected, shape = RoundedCornerShape(14.dp)) { Text("Launch error: $e", Modifier.padding(13.dp), color = TextPrimary, fontSize = 10.sp) } } }
        item { Text("Quick access", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        item { QuickGrid(onInstances, onMods, onSettings, onLogs) }
        item { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountCircle, null, tint = Accent); Spacer(Modifier.width(10.dp)); Column { Text(account, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("Account profile", color = Muted, fontSize = 9.sp) } } }
    }
}

@Composable private fun QuickGrid(instances: () -> Unit, mods: () -> Unit, settings: () -> Unit, logs: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickCard(Modifier.weight(1f), Icons.Default.ViewList, "Instances", "Profiles", instances); QuickCard(Modifier.weight(1f), Icons.Default.Extension, "Mods", "Discover + install", mods) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickCard(Modifier.weight(1f), Icons.Default.Settings, "Settings", "Java • graphics • controls", settings); QuickCard(Modifier.weight(1f), Icons.Default.Info, "Logs", "Launcher diagnostics", logs) }
    }
}
@Composable private fun QuickCard(m: Modifier, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) { Column(m.height(92.dp).background(Card, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = Accent); Column { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(sub, color = Muted, fontSize = 9.sp) } } }

@Composable private fun InstancesPage(list: List<LauncherInstance>, selected: LauncherInstance, onSelect: (LauncherInstance) -> Unit, onAdd: () -> Unit, onDelete: (String) -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { PageHeader("INSTANCES", "Independent Minecraft profiles") { }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onAdd, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" NEW") } }; Spacer(Modifier.height(10.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(list, key = { it.id }) { i -> Row(Modifier.fillMaxWidth().background(if (i.id == selected.id) Selected else Card, RoundedCornerShape(17.dp)).clickable { onSelect(i) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Accent); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(i.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("${i.minecraftVersion} • ${i.loader} • Java ${i.javaMajor}", color = Muted, fontSize = 9.sp); Text("${i.renderer} • ${i.maxRamMb} MB RAM", color = Muted, fontSize = 9.sp) }; IconButton(onClick = { onDelete(i.id) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) } } } } } }

@Composable private fun ModsPage(version: String, loader: String) { val provider: ModProvider = remember { ModrinthProvider() }; val installer = remember { ModInstaller(LocalContext.current) }; val scope = rememberCoroutineScope(); var projects by remember { mutableStateOf<List<ModProject>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var query by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var installing by remember { mutableStateOf<String?>(null) }; fun search() { scope.launch { loading = true; LauncherLog.add("Mod search: ${query.trim()}"); provider.search(query.trim(), version, loader).onSuccess { projects = it }.onFailure { message = it.message; LauncherLog.add(it.message ?: "Mod search failed", "ERROR") }; loading = false } }; LaunchedEffect(version, loader) { search() }; Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { PageHeader("MODS", "Recommendations • $version • $loader") {}; OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search Modrinth mods…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = ::search) { Icon(Icons.Default.ArrowForward, "Search") } }); Spacer(Modifier.height(9.dp)); message?.let { Text(it, color = Muted, fontSize = 10.sp) }; if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) { items(projects, key = { it.id }) { p -> Row(Modifier.fillMaxWidth().background(if (installing == p.id) Selected else Card, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Extension, null, tint = Accent); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(p.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(p.description, color = Muted, maxLines = 2, fontSize = 9.sp); Text("${p.downloads} downloads", color = Muted, fontSize = 8.sp) }; Button(enabled = installing != p.id, onClick = { scope.launch { installing = p.id; provider.getCompatibleFile(p.id, version, loader).fold({ file -> if (file == null) message = "No compatible release" else installer.installWithDependencies(file, provider, version, loader).fold({ message = "Installed ${it.size} file(s)"; LauncherLog.add("Installed ${it.size} mod file(s)") }, { message = it.message; LauncherLog.add(it.message ?: "Mod install failed", "ERROR") }) }, { message = it.message; LauncherLog.add(it.message ?: "Mod lookup failed", "ERROR") }); installing = null } }, contentPadding = PaddingValues(horizontal = 9.dp), shape = RoundedCornerShape(10.dp)) { Text(if (installing == p.id) "…" else "INSTALL", fontSize = 8.sp) } } } } } }

@Composable private fun SettingsRoot(page: SettingsPage, select: (SettingsPage) -> Unit, renderer: RendererBackend, setRenderer: (RendererBackend) -> Unit, instance: LauncherInstance, saveInstance: (LauncherInstance) -> Unit, openLogs: () -> Unit) {
    if (page == SettingsPage.GENERAL) SettingsMenu(select)
    else when (page) {
        SettingsPage.LAUNCHER -> LauncherSettings(instance)
        SettingsPage.JAVA -> JavaSettings(instance, saveInstance)
        SettingsPage.RENDERER -> RendererSettings(renderer, setRenderer)
        SettingsPage.CONTROLS -> ControlsSettings(instance, saveInstance)
        SettingsPage.PERFORMANCE -> PerformanceSettings(instance, saveInstance)
        SettingsPage.STORAGE -> StorageSettings()
        SettingsPage.LOGS -> { LogsPage(); LaunchedEffect(Unit) { } }
        SettingsPage.GENERAL -> Unit
    }
}

@Composable private fun SettingsMenu(select: (SettingsPage) -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { Text("SETTINGS", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Aether launcher configuration", color = Muted, fontSize = 10.sp); Spacer(Modifier.height(18.dp)); listOf(SettingsPage.LAUNCHER to (Icons.Default.Settings to "Launcher"), SettingsPage.JAVA to (Icons.Default.Terminal to "Java runtime"), SettingsPage.RENDERER to (Icons.Default.Bolt to "Renderer & graphics"), SettingsPage.CONTROLS to (Icons.Default.Gamepad to "Controls"), SettingsPage.PERFORMANCE to (Icons.Default.Speed to "Performance & memory"), SettingsPage.STORAGE to (Icons.Default.Storage to "Storage & downloads"), SettingsPage.LOGS to (Icons.Default.Info to "Logs & diagnostics")).forEach { (p, pair) -> SettingRow(pair.first, pair.second, "Configure launcher and per-instance behavior") { select(p) } } } }
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted) } }

@Composable private fun LauncherSettings(i: LauncherInstance) { val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("aether_launcher_settings", 0) }; var autoDownload by remember { mutableStateOf(prefs.getBoolean("auto_download", true)) }; var verify by remember { mutableStateOf(prefs.getBoolean("verify", true)) }; var keepOpen by remember { mutableStateOf(prefs.getBoolean("keep_open", false)) }; var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }; SettingsPanel("LAUNCHER", "Zaith-style grouped launcher controls") { Text("Selected instance: ${i.name}", color = Muted, fontSize = 10.sp); SwitchRow("Automatic downloads", "Prepare required Minecraft libraries and assets", autoDownload) { autoDownload = it; prefs.edit().putBoolean("auto_download", it).apply() }; SwitchRow("Verify downloads", "Check downloaded files before launch", verify) { verify = it; prefs.edit().putBoolean("verify", it).apply() }; SwitchRow("Keep launcher open", "Keep Aether available after starting Minecraft", keepOpen) { keepOpen = it; prefs.edit().putBoolean("keep_open", it).apply() }; SwitchRow("Compact launcher UI", "Use denser spacing on small screens", compact) { compact = it; prefs.edit().putBoolean("compact", it).apply() }; Text("Aether Launcher • Minecraft Java • Android", color = Muted, fontSize = 9.sp) } }

@Composable private fun JavaSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { val manager = remember { JavaRuntimeManager(LocalContext.current) }; var selected by remember { mutableIntStateOf(i.javaMajor) }; var ram by remember { mutableFloatStateOf(i.maxRamMb.toFloat()) }; SettingsPanel("JAVA RUNTIME", "Java ${i.javaMajor} • ${i.maxRamMb} MB heap") { val installed = listOf(8, 17, 21).mapNotNull { manager.findInstalled(it) }; Text("Installed / supported runtimes", color = Muted, fontSize = 11.sp); installed.forEach { r -> Choice("Java ${r.majorVersion}", r.majorVersion == selected) { selected = r.majorVersion; save(i.copy(javaMajor = selected)); LauncherLog.add("Java runtime changed to ${r.majorVersion}") } }; if (installed.isEmpty()) Text("No bundled runtime detected yet. The launcher will prepare the required runtime during launch.", color = Muted, fontSize = 10.sp); SliderBlock("Maximum RAM", ram, 768f..8192f, "${ram.toInt()} MB") { ram = it; save(i.copy(maxRamMb = it.toInt())) }; Text("Java 8 is commonly required by older Minecraft versions; modern releases normally use newer Java runtimes.", color = Muted, fontSize = 9.sp) } }

@Composable private fun RendererSettings(current: RendererBackend, set: (RendererBackend) -> Unit) { val ctx = LocalContext.current; val a = remember { RendererManager.availability(ctx) }; SettingsPanel("RENDERER", "Backend availability is detected from the device") { RendererBackend.entries.forEach { r -> val available = when (r) { RendererBackend.AUTO -> true; RendererBackend.OPENGL -> a.openGl; RendererBackend.VULKAN -> a.vulkan }; Choice("${r.label} ${if (available) "• available" else "• unavailable"}", current == r, available) { set(r) } }; Text("OpenGL ES uses the Pojav/LWJGL bridge. Vulkan availability does not by itself prove Minecraft Vulkan rendering works; device validation remains required.", color = Muted, fontSize = 9.sp) } }

@Composable private fun ControlsSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { var touch by remember { mutableStateOf(true) }; var mouse by remember { mutableStateOf(true) }; var gamepad by remember { mutableStateOf(true) }; var sensitivity by remember { mutableFloatStateOf(1f) }; SettingsPanel("CONTROLS", "Touch, mouse and controller") { SwitchRow("Touch controls", "On-screen Minecraft controls", touch) { touch = it }; SwitchRow("Virtual mouse", "Touch-to-mouse input", mouse) { mouse = it }; SwitchRow("Gamepad", "Android controller input", gamepad) { gamepad = it }; SliderBlock("Look sensitivity", sensitivity, .5f..2f, "${"%.1f".format(sensitivity)}×") { sensitivity = it }; Choice("Save control profile to instance", true) { save(i) } } }

@Composable private fun PerformanceSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { var fps by remember { mutableFloatStateOf(60f) }; var resolution by remember { mutableFloatStateOf(100f) }; var vsync by remember { mutableStateOf(true) }; SettingsPanel("PERFORMANCE", "Stable defaults for Android") { SliderBlock("FPS limit", fps, 30f..120f, "${fps.toInt()} FPS") { fps = it }; SliderBlock("Resolution scale", resolution, 50f..100f, "${resolution.toInt()}%") { resolution = it }; SwitchRow("VSync", "Reduce tearing and unnecessary GPU work", vsync) { vsync = it }; Choice("Balanced profile", true) { save(i) }; Choice("Battery saver profile", false) { } } }

@Composable private fun StorageSettings() { SettingsPanel("STORAGE", "Runtime, libraries, assets and instances") { Text("Aether keeps Minecraft data in its app-private storage and shares downloaded runtime/libraries where safe.", color = Muted, fontSize = 10.sp); Choice("Keep downloaded libraries", true) {}; Choice("Keep assets cache", true) {}; Choice("Verify downloads", true) {}; Choice("Safe cleanup mode", false) {} } }

@Composable private fun LogsPage() { var refresh by remember { mutableIntStateOf(0) }; var entries by remember(refresh) { mutableStateOf(LauncherLog.snapshot()) }; Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { PageHeader("LOGS", "Aether diagnostics • ${entries.size} buffered lines") { Row { OutlinedButton(onClick = { LauncherLog.add("Manual log refresh"); entries = LauncherLog.snapshot() }) { Text("REFRESH") }; Spacer(Modifier.width(8.dp)); Button(onClick = { LauncherLog.clear(); refresh++ }, shape = RoundedCornerShape(10.dp)) { Text("CLEAR") } } }; Spacer(Modifier.height(10.dp)); if (entries.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No launcher logs yet.", color = Muted, fontSize = 12.sp) } } else LazyColumn(Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(entries) { line -> Text(line, color = TextPrimary, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) } } } }

@Composable private fun SettingsPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { LazyColumn(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp); Text(subtitle, color = Muted, fontSize = 10.sp) }; item { Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content) } } }
@Composable private fun Choice(title: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(if (selected) Selected else Card, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), color = if (enabled) TextPrimary else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); if (selected) Icon(Icons.Default.CheckCircle, null, tint = Accent) } }
@Composable private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Switch(checked, onChange) } }
@Composable private fun SliderBlock(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(13.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(valueText, color = Accent, fontSize = 11.sp) }; Slider(value = value, onValueChange = onChange, valueRange = range) } }
@Composable private fun PageHeader(title: String, subtitle: String, trailing: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text(subtitle, color = Muted, fontSize = 10.sp) }; trailing() } }
