package com.example.aetherlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aetherlauncher.minecraft.MinecraftRepository
import com.example.aetherlauncher.minecraft.MinecraftVersion
import com.example.aetherlauncher.minecraft.mods.ModInstaller
import com.example.aetherlauncher.minecraft.mods.ModProject
import com.example.aetherlauncher.minecraft.mods.ModrinthProvider
import kotlinx.coroutines.launch

private val Bg = Color(0xFF08090C)
private val Card = Color(0xFF111318)
private val CardSelected = Color(0xFF1B1728)
private val Accent = Color(0xFF7C4DFF)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFF9296A1)
private val Success = Color(0xFF42D392)

private enum class Screen { HOME, VERSIONS, MODS, CONTROLS, RENDERING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AetherTheme { AetherApp() } }
    }
}

@Composable
private fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent, onPrimary = Color.White, onBackground = TextPrimary, onSurface = TextPrimary),
        content = content
    )
}

@Composable
private fun AetherApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedVersion by remember { mutableStateOf("1.21.8") }
    when (screen) {
        Screen.HOME -> HomeScreen(selectedVersion, { screen = Screen.VERSIONS }, { screen = Screen.MODS }, { screen = Screen.CONTROLS }, { screen = Screen.RENDERING })
        Screen.VERSIONS -> VersionsScreen(selectedVersion, { screen = Screen.HOME }) { selectedVersion = it; screen = Screen.HOME }
        Screen.MODS -> ModsScreen(selectedVersion) { screen = Screen.HOME }
        Screen.CONTROLS -> SimpleFeatureScreen("CONTROLS", "Touch, keyboard and controller mapping will live here.") { screen = Screen.HOME }
        Screen.RENDERING -> SimpleFeatureScreen("RENDERING", "Renderer profiles and low-end optimization will live here.") { screen = Screen.HOME }
    }
}

@Composable
private fun HomeScreen(selectedVersion: String, onVersions: () -> Unit, onMods: () -> Unit, onControls: () -> Unit, onRendering: () -> Unit) {
    var toast by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = Bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("AETHER", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("JAVA LAUNCHER", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                IconButton(onClick = { toast = "Microsoft account will be added in Phase 2.8" }) { Icon(Icons.Default.Person, "Account", tint = TextPrimary) }
            }
            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).clickable(onClick = onVersions).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).background(Accent.copy(alpha = .22f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Gamepad, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text("Minecraft Java", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Vanilla $selectedVersion", color = TextMuted, fontSize = 13.sp) }; Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                }
                Spacer(Modifier.height(18.dp)); HorizontalDivider(color = Color.White.copy(alpha = .06f)); Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { InfoItem(Icons.Default.Memory, "RAM", "2 GB"); InfoItem(Icons.Default.Speed, "FPS", "--"); InfoItem(Icons.Default.Tune, "Renderer", "Auto") }
            }
            Button(onClick = { toast = "Game engine comes after installation + Java runtime phases" }, Modifier.fillMaxWidth().height(66.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("PLAY", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Text("Launcher tools", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { ToolCard(Modifier.weight(1f), Icons.Default.Extension, "Mods", "Modrinth installer", onMods); ToolCard(Modifier.weight(1f), Icons.Default.Gamepad, "Controls", "Touch controls", onControls) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { ToolCard(Modifier.weight(1f), Icons.Default.ViewList, "Versions", "Live Mojang list", onVersions); ToolCard(Modifier.weight(1f), Icons.Default.Bolt, "Rendering", "Graphics", onRendering) }
            Spacer(Modifier.weight(1f)); Text("AETHER LAUNCHER • PHASE 2.3", Modifier.fillMaxWidth(), color = TextMuted.copy(alpha = .6f), fontSize = 10.sp, textAlign = TextAlign.Center, letterSpacing = 1.5.sp)
        }
    }
    toast?.let { message ->
        LaunchedEffect(message) { kotlinx.coroutines.delay(1800); toast = null }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Surface(Modifier.padding(bottom = 30.dp), shape = RoundedCornerShape(14.dp), color = Card) { Text(message, Modifier.padding(18.dp), color = TextPrimary, fontSize = 12.sp) } }
    }
}

@Composable private fun InfoItem(icon: ImageVector, title: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp)); Spacer(Modifier.height(5.dp)); Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(title, color = TextMuted, fontSize = 10.sp) } }

@Composable private fun ToolCard(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (true) 1f else .98f, tween(180), label = "toolScale")
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.height(105.dp).background(Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(23.dp)); Column { Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 10.sp) }
    }
}

@Composable
private fun ModsScreen(selectedVersion: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val provider = remember { ModrinthProvider() }
    val installer = remember { ModInstaller(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loader by remember { mutableStateOf("Fabric") }
    var results by remember { mutableStateOf<List<ModProject>>(emptyList()) }
    var installed by remember { mutableStateOf(installer.installedMods()) }
    var loading by remember { mutableStateOf(false) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun searchMods() {
        if (query.isBlank()) return
        scope.launch {
            loading = true; message = null
            provider.search(query.trim(), selectedVersion, loader).onSuccess { results = it }.onFailure { message = it.message ?: "Unable to search Modrinth" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Header("MODS", "Modrinth • $selectedVersion", onBack)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search mods...", color = TextMuted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { searchMods() }) { Icon(Icons.Default.ArrowForward, "Search") } }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fabric", "Forge", "NeoForge", "Quilt", "Vanilla").forEach { option ->
                val selected by animateColorAsState(if (loader == option) Accent else Card, tween(180), label = "loaderColor")
                Surface(onClick = { loader = option }, shape = RoundedCornerShape(12.dp), color = selected) { Text(option, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Card) }
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(message != null, enter = fadeIn() + scaleIn(), exit = fadeOut()) { message?.let { Surface(color = Card, shape = RoundedCornerShape(14.dp)) { Text(it, Modifier.padding(14.dp), color = TextMuted, fontSize = 11.sp) } } }
        if (query.isBlank() && results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Extension, null, tint = Accent, modifier = Modifier.size(50.dp)); Spacer(Modifier.height(12.dp)); Text("Find mods for $selectedVersion", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Search Modrinth and install compatible .jar files.", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center) } }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(results, key = { it.id }) { project ->
                    ModCard(project, installingId == project.id) {
                        scope.launch {
                            installingId = project.id; message = null
                            provider.getCompatibleFile(project.id, selectedVersion, loader).fold(
                                onSuccess = { file -> if (file == null) message = "No compatible $loader file found for $selectedVersion" else installer.installWithDependencies(file, provider, selectedVersion, loader).fold({ installed = installer.installedMods(); message = "Installed ${it.size} file(s)" }, { message = it.message ?: "Installation failed" }) },
                                onFailure = { message = it.message ?: "Unable to load mod version" }
                            )
                            installingId = null
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp)); Text("Installed", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (installed.isEmpty()) Text("No mods installed yet.", Modifier.padding(vertical = 12.dp), color = TextMuted, fontSize = 11.sp)
                    installed.forEach { file -> Surface(Modifier.fillMaxWidth().padding(top = 7.dp), color = Card, shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(file.name, Modifier.weight(1f), color = TextPrimary, fontSize = 11.sp); IconButton(onClick = { if (installer.remove(file)) installed = installer.installedMods() }) { Icon(Icons.Default.DeleteOutline, "Remove", tint = TextMuted) } } } }
                }
            }
        }
    }
}

@Composable private fun ModCard(project: ModProject, installing: Boolean, onInstall: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(220)) + slideInHorizontally(initialOffsetX = { 40 }, animationSpec = tween(220))) {
        val container by animateColorAsState(if (installing) CardSelected else Card, tween(180), label = "modCardColor")
        Row(Modifier.fillMaxWidth().background(container, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = Accent, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(project.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(project.description, color = TextMuted, fontSize = 10.sp, maxLines = 2); Spacer(Modifier.height(4.dp)); Text("${project.downloads} downloads • Modrinth", color = TextMuted.copy(alpha = .75f), fontSize = 9.sp) }
            Spacer(Modifier.width(8.dp)); Button(onClick = onInstall, enabled = !installing, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { if (installing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Text("INSTALL", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun VersionsScreen(selectedVersion: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val repository = remember { MinecraftRepository() }; val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }; var latestRelease by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var search by remember { mutableStateOf("") }; var selected by remember { mutableStateOf(selectedVersion) }
    fun loadVersions() { scope.launch { loading = true; error = null; repository.fetchVersionManifest().onSuccess { versions = it.versions; latestRelease = it.latestRelease }.onFailure { error = it.message ?: "Unable to load Minecraft versions" }; loading = false } }
    LaunchedEffect(Unit) { loadVersions() }
    val filtered = versions.filter { it.id.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Header("VERSIONS", "Live Minecraft Java metadata", onBack); Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search version...", color = TextMuted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { loadVersions() }) { Icon(Icons.Default.Refresh, "Refresh") } }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
        Spacer(Modifier.height(12.dp))
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Accent); Spacer(Modifier.height(14.dp)); Text("Connecting to Minecraft metadata...", color = TextMuted, fontSize = 12.sp) } }
        else if (error != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CloudOff, null, tint = Accent, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(12.dp)); Text("Couldn't load versions", color = TextPrimary, fontWeight = FontWeight.Bold); Text(error!!, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center); Spacer(Modifier.height(16.dp)); Button(onClick = { loadVersions() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("RETRY") } } }
        else { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("LATEST RELEASE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(latestRelease ?: "Unknown", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }; Text("${versions.size} versions", color = TextMuted, fontSize = 11.sp) }; Spacer(Modifier.height(12.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(filtered, key = { it.id }) { version -> VersionCard(version, selected == version.id, latestRelease == version.id) { selected = version.id } }; item { Spacer(Modifier.height(4.dp)); Button(onClick = { onSelect(selected) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("USE $selected", fontWeight = FontWeight.Bold) } } } }
    }
}

@Composable private fun VersionCard(version: MinecraftVersion, selected: Boolean, latest: Boolean, onClick: () -> Unit) { val bg by animateColorAsState(if (selected) CardSelected else Card, tween(180), label = "versionColor"); Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Accent.copy(alpha = .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ViewInAr, null, tint = Accent) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(version.id, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold); if (latest) { Spacer(Modifier.width(8.dp)); Text("LATEST", color = Success, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }; Text(version.type.replaceFirstChar { it.uppercase() }, color = TextMuted, fontSize = 11.sp) }; Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.ChevronRight, null, tint = if (selected) Accent else TextMuted) } }

@Composable private fun Header(title: String, subtitle: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }; Spacer(Modifier.width(14.dp)); Column { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text(subtitle, color = TextMuted, fontSize = 11.sp) } } }

@Composable private fun SimpleFeatureScreen(title: String, message: String, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { Header(title, "Aether Launcher", onBack); Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Construction, null, tint = Accent, modifier = Modifier.size(54.dp)); Spacer(Modifier.height(16.dp)); Text(message, color = TextMuted, textAlign = TextAlign.Center, fontSize = 13.sp) } } } }
