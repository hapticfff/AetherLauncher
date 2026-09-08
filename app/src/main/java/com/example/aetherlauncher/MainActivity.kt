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
private enum class Page { HOME, INSTANCES, MODS, SETTINGS }
private enum class SettingsPage { GENERAL, JAVA, RENDERER, CONTROLS, PERFORMANCE, STORAGE }

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
        if (accounts.isEmpty()) accountStore.addOfflineDemo("Offline account")
        accounts = accountStore.accounts(); account = accountStore.selected() ?: accounts.firstOrNull()
    }
    BackHandler(enabled = page != Page.HOME) { if (page == Page.SETTINGS && settingsPage != SettingsPage.GENERAL) settingsPage = SettingsPage.GENERAL else page = Page.HOME }

    fun launch() {
        if (launching) return
        if (account?.type != AccountType.OFFLINE_DEMO) { error = "Select a supported local test account"; return }
        launching = true; error = null; progress = "Preparing ${selected.minecraftVersion} • ${selected.renderer}…"
        scope.launch {
            val r = RendererBackend.fromLabel(selected.renderer)
            launchEngine.installAndLaunchDemo(selected.minecraftVersion, r) { progress = it }
                .onSuccess { progress = "Minecraft process started"; launching = false }
                .onFailure { error = it.message ?: "Launch failed"; launching = false }
        }
    }

    Scaffold(containerColor = Bg, bottomBar = { NavigationBar(containerColor = Card) {
        NavItem("Play", Icons.Default.PlayArrow, page == Page.HOME) { page = Page.HOME }
        NavItem("Instances", Icons.Default.ViewList, page == Page.INSTANCES) { page = Page.INSTANCES }
        NavItem("Mods", Icons.Default.Extension, page == Page.MODS) { page = Page.MODS }
        NavItem("Settings", Icons.Default.Settings, page == Page.SETTINGS) { page = Page.SETTINGS }
    } }) { pad -> Box(Modifier.padding(pad).fillMaxSize()) {
        when (page) {
            Page.HOME -> HomePage(selected, account?.name ?: "No account", renderer, launching, progress, error, { launch() }, { page = Page.INSTANCES }, { page = Page.MODS }, { page = Page.SETTINGS })
            Page.INSTANCES -> InstancesPage(list, selected, { instance -> selected = instance; page = Page.HOME }, { val n = LauncherInstance(java.util.UUID.randomUUID().toString(), "New Instance", selected.minecraftVersion, selected.loader, selected.javaMajor, selected.renderer, selected.maxRamMb); instances.save(n); list = instances.list() }, { id -> instances.delete(id); list = instances.list(); if (selected.id == id) selected = instances.ensureDefault() })
            Page.MODS -> ModsPage(selected.minecraftVersion, selected.loader)
            Page.SETTINGS -> SettingsRoot(settingsPage, { settingsPage = it }, renderer, { renderer = it; rendererStore.set(it); selected = selected.copy(renderer = it.label); instances.save(selected); list = instances.list() }, selected, { value -> selected = value; instances.save(value); list = instances.list() })
        }
    } }
}

@Composable private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) { NavigationBarItem(selected = selected, onClick = onClick, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }) }

@Composable private fun HomePage(i: LauncherInstance, account: String, renderer: RendererBackend, launching: Boolean, progress: String, error: String?, onPlay: () -> Unit, onInstances: () -> Unit, onMods: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("AETHER", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("MINECRAFT JAVA LAUNCHER", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp) }; Icon(Icons.Default.CloudDone, "Ready", tint = Good) } }
        item { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).background(Accent.copy(alpha = .18f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.SportsEsports, null, tint = Accent, modifier = Modifier.size(31.dp)) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(i.name, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Minecraft ${i.minecraftVersion} • ${i.loader}", color = Muted, fontSize = 11.sp); Text("Java ${i.javaMajor} • ${i.maxRamMb} MB • ${i.renderer}", color = Muted, fontSize = 10.sp) } }; Spacer(Modifier.height(16.dp)); Button(onClick = onPlay, enabled = !launching, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(17.dp)) { Icon(if (launching) Icons.Default.HourglassTop else Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (launching) "LAUNCHING" else "PLAY", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) } }; }
        if (launching) item { Text(progress, Modifier.fillMaxWidth(), color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center) }
        error?.let { e -> item { Surface(Modifier.fillMaxWidth(), color = Selected, shape = RoundedCornerShape(14.dp)) { Text("Launch error: $e", Modifier.padding(13.dp), color = TextPrimary, fontSize = 10.sp) } } }
        item { Text("Quick access", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        item { QuickGrid(onInstances, onMods, onSettings) }
        item { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountCircle, null, tint = Accent); Spacer(Modifier.width(10.dp)); Column { Text(account, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("Account profile", color = Muted, fontSize = 9.sp) } } }
    }
}

@Composable private fun QuickGrid(instances: () -> Unit, mods: () -> Unit, settings: () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), Modifier.fillMaxWidth()) { QuickCard(Modifier.weight(1f), Icons.Default.ViewList, "Instances", "Profiles", instances); QuickCard(Modifier.weight(1f), Icons.Default.Extension, "Mods", "Discover + install", mods) }; Row(horizontalArrangement = Arrangement.spacedBy(10.dp), Modifier.fillMaxWidth()) { QuickCard(Modifier.weight(1f), Icons.Default.Settings, "Settings", "Java • graphics • controls", settings); QuickCard(Modifier.weight(1f), Icons.Default.Speed, "Optimization", "Performance profiles", settings) } } }
@Composable private fun QuickCard(m: Modifier, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) { Column(m.height(92.dp).background(Card, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = Accent); Column { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(sub, color = Muted, fontSize = 9.sp) } } }

@Composable private fun InstancesPage(list: List<LauncherInstance>, selected: LauncherInstance, onSelect: (LauncherInstance) -> Unit, onAdd: () -> Unit, onDelete: (String) -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { PageHeader("INSTANCES", "Independent Minecraft profiles") { }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onAdd, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" NEW") } }; Spacer(Modifier.height(10.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(list, key = { it.id }) { i -> Row(Modifier.fillMaxWidth().background(if (i.id == selected.id) Selected else Card, RoundedCornerShape(17.dp)).clickable { onSelect(i) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Accent); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(i.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("${i.minecraftVersion} • ${i.loader} • Java ${i.javaMajor}", color = Muted, fontSize = 9.sp); Text("${i.renderer} • ${i.maxRamMb} MB RAM", color = Muted, fontSize = 9.sp) }; IconButton(onClick = { onDelete(i.id) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) } } } } } }

@Composable private fun ModsPage(version: String, loader: String) { val provider: ModProvider = remember { ModrinthProvider() }; val installer = remember { ModInstaller(LocalContext.current) }; val scope = rememberCoroutineScope(); var projects by remember { mutableStateOf<List<ModProject>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var query by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var installing by remember { mutableStateOf<String?>(null) }; fun search() { scope.launch { loading = true; provider.search(query.trim(), version, loader).onSuccess { projects = it }.onFailure { message = it.message }; loading = false } }; LaunchedEffect(version, loader) { search() }; Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { PageHeader("MODS", "Recommendations • $version • $loader") {}; OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search Modrinth mods…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = ::search) { Icon(Icons.Default.ArrowForward, "Search") } }); Spacer(Modifier.height(9.dp)); message?.let { Text(it, color = Muted, fontSize = 10.sp) }; if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) { items(projects, key = { it.id }) { p -> Row(Modifier.fillMaxWidth().background(if (installing == p.id) Selected else Card, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Extension, null, tint = Accent); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(p.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(p.description, color = Muted, maxLines = 2, fontSize = 9.sp); Text("${p.downloads} downloads", color = Muted, fontSize = 8.sp) }; Button(enabled = installing != p.id, onClick = { scope.launch { installing = p.id; provider.getCompatibleFile(p.id, version, loader).fold({ file -> if (file == null) message = "No compatible release" else installer.installWithDependencies(file, provider, version, loader).fold({ message = "Installed ${it.size} file(s)" }, { message = it.message }) }, { message = it.message }); installing = null } }, contentPadding = PaddingValues(horizontal = 9.dp), shape = RoundedCornerShape(10.dp)) { Text(if (installing == p.id) "…" else "INSTALL", fontSize = 8.sp) } } } } } }

@Composable private fun SettingsRoot(page: SettingsPage, select: (SettingsPage) -> Unit, renderer: RendererBackend, setRenderer: (RendererBackend) -> Unit, instance: LauncherInstance, saveInstance: (LauncherInstance) -> Unit) { if (page == SettingsPage.GENERAL) SettingsMenu(select) else when (page) { SettingsPage.JAVA -> JavaSettings(instance, saveInstance); SettingsPage.RENDERER -> RendererSettings(renderer, setRenderer); SettingsPage.CONTROLS -> ControlsSettings(instance, saveInstance); SettingsPage.PERFORMANCE -> PerformanceSettings(instance, saveInstance); SettingsPage.STORAGE -> StorageSettings(); SettingsPage.GENERAL -> Unit } }

@Composable private fun SettingsMenu(select: (SettingsPage) -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { Text("SETTINGS", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Complete launcher configuration", color = Muted, fontSize = 10.sp); Spacer(Modifier.height(18.dp)); listOf(SettingsPage.JAVA to (Icons.Default.Terminal to "Java runtime"), SettingsPage.RENDERER to (Icons.Default.Bolt to "Renderer & graphics"), SettingsPage.CONTROLS to (Icons.Default.Gamepad to "Controls"), SettingsPage.PERFORMANCE to (Icons.Default.Speed to "Performance & memory"), SettingsPage.STORAGE to (Icons.Default.Storage to "Storage & downloads")).forEach { (p, pair) -> SettingRow(pair.first, pair.second, "Configure launcher and per-instance behavior") { select(p) } } } }
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Card, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted) } }

@Composable private fun JavaSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { val manager = remember { JavaRuntimeManager(LocalContext.current) }; var selected by remember { mutableIntStateOf(i.javaMajor) }; var ram by remember { mutableFloatStateOf(i.maxRamMb.toFloat()) }; SettingsPanel("JAVA RUNTIME", "Java ${i.javaMajor} • ${i.maxRamMb} MB heap") { val installed = listOf(8, 17, 21).mapNotNull { manager.findInstalled(it) }; Text("Installed / supported runtimes", color = Muted, fontSize = 11.sp); installed.forEach { r -> Choice("Java ${r.majorVersion}", r.majorVersion == selected) { selected = r.majorVersion; save(i.copy(javaMajor = selected)) } }; if (installed.isEmpty()) Text("No bundled runtime detected yet. The launcher will prepare the required runtime during launch.", color = Muted, fontSize = 10.sp); SliderBlock("Maximum RAM", ram, 768f..8192f, "${ram.toInt()} MB") { ram = it; save(i.copy(maxRamMb = it.toInt())) }; Text("Java 8 is commonly required by older Minecraft versions; modern releases normally use newer Java runtimes.", color = Muted, fontSize = 9.sp) } }

@Composable private fun RendererSettings(current: RendererBackend, set: (RendererBackend) -> Unit) { val ctx = LocalContext.current; val a = remember { RendererManager.availability(ctx) }; SettingsPanel("RENDERER", "Backend availability is detected from the device") { RendererBackend.entries.forEach { r -> val available = when (r) { RendererBackend.AUTO -> true; RendererBackend.OPENGL -> a.openGl; RendererBackend.VULKAN -> a.vulkan }; Choice("${r.label} ${if (available) "• available" else "• unavailable"}", current == r, available) { set(r) } }; Text("OpenGL ES uses the Pojav/LWJGL bridge. Vulkan availability does not by itself prove Minecraft Vulkan rendering works; device validation remains required.", color = Muted, fontSize = 9.sp) } }

@Composable private fun ControlsSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { var touch by remember { mutableStateOf(true) }; var mouse by remember { mutableStateOf(true) }; var gamepad by remember { mutableStateOf(true) }; var sensitivity by remember { mutableFloatStateOf(1f) }; SettingsPanel("CONTROLS", "Touch, mouse and controller") { SwitchRow("Touch controls", "On-screen Minecraft controls", touch) { touch = it }; SwitchRow("Virtual mouse", "Touch-to-mouse input", mouse) { mouse = it }; SwitchRow("Gamepad", "Android controller input", gamepad) { gamepad = it }; SliderBlock("Look sensitivity", sensitivity, .5f..2f, "${"%.1f".format(sensitivity)}×") { sensitivity = it }; Choice("Save control profile to instance", true) { save(i) } } }

@Composable private fun PerformanceSettings(i: LauncherInstance, save: (LauncherInstance) -> Unit) { var fps by remember { mutableFloatStateOf(60f) }; var resolution by remember { mutableFloatStateOf(100f) }; var vsync by remember { mutableStateOf(true) }; SettingsPanel("PERFORMANCE", "Stable defaults for Android") { SliderBlock("FPS limit", fps, 30f..120f, "${fps.toInt()} FPS") { fps = it }; SliderBlock("Resolution scale", resolution, 50f..100f, "${resolution.toInt()}%") { resolution = it }; SwitchRow("VSync", "Reduce tearing and unnecessary GPU work", vsync) { vsync = it }; Choice("Balanced profile", true) { save(i) }; Choice("Battery saver profile", false) { } } }

@Composable private fun StorageSettings() { SettingsPanel("STORAGE", "Runtime, libraries, assets and instances") { Text("Aether keeps Minecraft data in its app-private storage and shares downloaded runtime/libraries where safe.", color = Muted, fontSize = 10.sp); Choice("Keep downloaded libraries", true) {}; Choice("Keep assets cache", true) {}; Choice("Verify downloads", true) {}; Choice("Safe cleanup mode", false) {} } }

@Composable private fun SettingsPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { LazyColumn(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp); Text(subtitle, color = Muted, fontSize = 10.sp) }; item { Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content) } } }
@Composable private fun Choice(title: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(if (selected) Selected else Card, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), color = if (enabled) TextPrimary else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); if (selected) Icon(Icons.Default.CheckCircle, null, tint = Accent) } }
@Composable private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Switch(checked, onChange) } }
@Composable private fun SliderBlock(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(13.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(valueText, color = Accent, fontSize = 11.sp) }; Slider(value = value, onValueChange = onChange, valueRange = range) } }
@Composable private fun PageHeader(title: String, subtitle: String, trailing: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text(subtitle, color = Muted, fontSize = 10.sp) }; trailing() } }
