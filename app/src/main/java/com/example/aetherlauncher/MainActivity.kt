package com.example.aetherlauncher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Bg = Color(0xFF08090C)
private val Card = Color(0xFF111318)
private val CardSelected = Color(0xFF1B1728)
private val Accent = Color(0xFF7C4DFF)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFF9296A1)
private val Success = Color(0xFF42D392)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AetherTheme {
                var screen by remember { mutableStateOf("home") }
                when (screen) {
                    "home" -> HomeScreen(
                        onVersions = { screen = "versions" },
                        onMods = { screen = "mods" },
                        onControls = { screen = "controls" },
                        onRendering = { screen = "rendering" }
                    )
                    "versions" -> VersionsScreen { screen = "home" }
                    "mods" -> ModsScreen { screen = "home" }
                    "controls" -> ControlsScreen { screen = "home" }
                    "rendering" -> RenderingScreen { screen = "home" }
                }
            }
        }
    }
}

@Composable
private fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Card,
            primary = Accent,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@Composable
private fun HomeScreen(
    onVersions: () -> Unit,
    onMods: () -> Unit,
    onControls: () -> Unit,
    onRendering: () -> Unit
) {
    var message by remember { mutableStateOf(false) }

    Scaffold(containerColor = Bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AETHER", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("JAVA LAUNCHER", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                IconButton(onClick = { message = true }) {
                    Icon(Icons.Default.Person, "Account", tint = TextPrimary)
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).clickable(onClick = onVersions).padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).background(Accent.copy(alpha = .22f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Gamepad, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Minecraft Java", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Fabric 1.21.8", color = TextMuted, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .06f))
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoItem(Icons.Default.Memory, "RAM", "2 GB")
                    InfoItem(Icons.Default.Speed, "FPS", "--")
                    InfoItem(Icons.Default.Tune, "Renderer", "Auto")
                }
            }

            Button(
                onClick = { message = true },
                Modifier.fillMaxWidth().height(66.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("PLAY", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }

            Text("Launcher tools", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(Modifier.weight(1f), Icons.Default.Extension, "Mods", "Manage mods", onMods)
                ToolCard(Modifier.weight(1f), Icons.Default.Gamepad, "Controls", "Touch controls", onControls)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard(Modifier.weight(1f), Icons.Default.ViewList, "Versions", "Game versions", onVersions)
                ToolCard(Modifier.weight(1f), Icons.Default.Bolt, "Rendering", "Graphics", onRendering)
            }

            Spacer(Modifier.weight(1f))
            Text("AETHER LAUNCHER • UI PREVIEW", Modifier.fillMaxWidth(), color = TextMuted.copy(alpha = .6f), fontSize = 10.sp, textAlign = TextAlign.Center, letterSpacing = 1.5.sp)
        }
    }

    if (message) {
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); message = false }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.padding(bottom = 30.dp), shape = RoundedCornerShape(14.dp), color = Card) {
                Text("Minecraft core not connected yet", Modifier.padding(18.dp), color = TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(title, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ToolCard(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier.height(105.dp).background(Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(23.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 10.sp)
        }
    }
}

private data class Version(val version: String, val type: String)

@Composable
private fun VersionsScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf("Minecraft") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val versions = listOf("1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.20.6", "1.20.4", "1.19.4").mapIndexed { i, v -> Version(v, if (i == 0) "Latest" else "Release") }
    val filtered = versions.filter { it.version.contains(search, true) }

    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Header("VERSIONS", "Minecraft installations", onBack)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search version...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Clear, "Clear") } },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Minecraft", "Fabric", "Forge").forEach { name ->
                        VersionTab(name, tab == name, Modifier.weight(1f)) { tab = name; selected = null }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("NeoForge", "Quilt").forEach { name ->
                        VersionTab(name, tab == name, Modifier.weight(1f)) { tab = name; selected = null }
                    }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            items(filtered, key = { it.version }) { v -> VersionCard(v, tab, selected == v.version) { selected = v.version } }
            item {
                if (selected != null) Button({}, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("SELECT $tab $selected", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text(subtitle, color = TextMuted, fontSize = 11.sp) }
    }
}

@Composable
private fun VersionTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).background(if (selected) Accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(title, color = if (selected) Color.White else TextMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun VersionCard(version: Version, loader: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) CardSelected else Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(Accent.copy(alpha = .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ViewInAr, null, tint = Accent) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(version.version, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(loader, color = TextMuted, fontSize = 11.sp)
            if (version.type == "Latest") Text("LATEST", color = Success, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.ChevronRight, null, tint = if (selected) Accent else TextMuted)
    }
}

private data class InstalledMod(val id: String, val name: String, val fileName: String, val uri: Uri, val enabled: Boolean = true)

@Composable
private fun ModsScreen(onBack: () -> Unit) {
    var mods by remember { mutableStateOf(emptyList<InstalledMod>()) }
    var search by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val additions = uris.filter { it.toString().lowercase().endsWith(".jar") }.mapIndexed { i, uri ->
            val file = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown.jar"
            InstalledMod("${uri}_$i", file.removeSuffix(".jar").replace("-", " ").replace("_", " "), file, uri)
        }
        mods = (mods + additions).distinctBy { it.uri.toString() }
    }
    val filtered = mods.filter { it.name.contains(search, true) || it.fileName.contains(search, true) }

    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text("MODS", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("${mods.size} installed", color = TextMuted, fontSize = 11.sp) }
            IconButton({ info = true }) { Icon(Icons.Default.Info, "Info", tint = TextMuted) }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search installed mods...", color = TextMuted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("application/java-archive", "application/octet-stream")) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("ADD JAR MODS", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ExtensionOff, null, tint = Accent, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(12.dp)); Text(if (mods.isEmpty()) "No mods installed" else "No matching mods", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Select .jar files to add them", color = TextMuted, fontSize = 12.sp) }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(filtered, key = { it.id }) { mod -> ModCard(mod, { mods = mods.map { if (it.id == mod.id) it.copy(enabled = !it.enabled) else it } }, { mods = mods.filterNot { it.id == mod.id } }) } }
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Mods Manager") }, text = { Text("Select JAR files and manage their UI state. This prototype does not yet copy them into a Minecraft instance.") }, confirmButton = { TextButton({ info = false }) { Text("OK") } })
}

@Composable
private fun ModCard(mod: InstalledMod, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (mod.enabled) Card else Color(0xFF0D0E11), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).background(Accent.copy(alpha = .14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = if (mod.enabled) Accent else TextMuted) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(mod.name, color = if (mod.enabled) TextPrimary else TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(mod.fileName, color = TextMuted, fontSize = 10.sp, maxLines = 1); Text(if (mod.enabled) "ENABLED" else "DISABLED", color = if (mod.enabled) Success else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        Switch(checked = mod.enabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = TextMuted) }
    }
}

private data class ControlButton(val id: String, val label: String, val x: Float, val y: Float, val size: Float, val visible: Boolean = true)

@Composable
private fun ControlsScreen(onBack: () -> Unit) {
    var controls by remember { mutableStateOf(listOf(ControlButton("joystick", "◉", 30f, 430f, 100f), ControlButton("jump", "JUMP", 290f, 420f, 75f), ControlButton("attack", "⚔", 300f, 330f, 65f), ControlButton("sneak", "SNEAK", 170f, 500f, 65f), ControlButton("inventory", "INV", 30f, 330f, 60f))) }
    var selected by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text("CONTROLS", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Drag buttons to customize", color = TextMuted, fontSize = 11.sp) }
            IconButton({ saved = true }) { Icon(Icons.Default.Save, "Save", tint = Accent) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).background(Color(0xFF0D0F13), RoundedCornerShape(22.dp))) {
            controls.filter { it.visible }.forEach { control ->
                Box(Modifier.offset { IntOffset(control.x.roundToInt(), control.y.roundToInt()) }.size(control.size.dp).background(if (selected == control.id) Accent.copy(alpha = .45f) else Color(0xFF191C22), RoundedCornerShape(18.dp)).pointerInput(control.id) { detectDragGestures(onDragStart = { selected = control.id }, onDrag = { change, amount -> change.consume(); controls = controls.map { if (it.id == control.id) it.copy(x = (it.x + amount.x).coerceIn(0f, 600f), y = (it.y + amount.y).coerceIn(0f, 700f)) else it } }) }, contentAlignment = Alignment.Center) { Text(control.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { controls = listOf(ControlButton("joystick", "◉", 30f, 430f, 100f), ControlButton("jump", "JUMP", 290f, 420f, 75f), ControlButton("attack", "⚔", 300f, 330f, 65f), ControlButton("sneak", "SNEAK", 170f, 500f, 65f), ControlButton("inventory", "INV", 30f, 330f, 60f)); selected = null }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(5.dp)); Text("RESET") }
            Button({ saved = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("SAVE") }
        }
        Button({ showList = true }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Card)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("CONTROL VISIBILITY") }
        Spacer(Modifier.height(14.dp))
    }
    if (saved) { LaunchedEffect(Unit) { kotlinx.coroutines.delay(1000); saved = false }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Surface(Modifier.padding(bottom = 30.dp), shape = RoundedCornerShape(14.dp), color = Card) { Text("Control layout saved (prototype)", Modifier.padding(18.dp), color = TextPrimary, fontSize = 12.sp) } } }
    if (showList) ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = Card) { Text("CONTROL VISIBILITY", Modifier.padding(horizontal = 20.dp), color = TextPrimary, fontWeight = FontWeight.Bold); LazyColumn(contentPadding = PaddingValues(20.dp)) { items(controls, key = { it.id }) { c -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(c.id, Modifier.weight(1f), color = TextPrimary, fontWeight = FontWeight.Bold); Switch(checked = c.visible, onCheckedChange = { controls = controls.map { if (it.id == c.id) it.copy(visible = !it.visible) else it } }) } } } }
}

private enum class Renderer { AUTOMATIC, OPENGL, VULKAN }

@Composable
private fun RenderingScreen(onBack: () -> Unit) {
    var renderer by remember { mutableStateOf(Renderer.AUTOMATIC) }
    var resolution by remember { mutableFloatStateOf(100f) }
    var fps by remember { mutableFloatStateOf(60f) }
    var vsync by remember { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text("RENDERING", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Graphics & renderer", color = TextMuted, fontSize = 11.sp) }
            Icon(Icons.Default.SettingsSuggest, null, tint = Accent)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item { SectionTitle("RENDERER", "Choose how Minecraft draws graphics") }
            item { RendererOption("Automatic", "Let Aether choose the renderer", Icons.Default.AutoAwesome, renderer == Renderer.AUTOMATIC) { renderer = Renderer.AUTOMATIC } }
            item { RendererOption("OpenGL", "Compatibility renderer", Icons.Default.Layers, renderer == Renderer.OPENGL) { renderer = Renderer.OPENGL } }
            item { RendererOption("Vulkan", "Modern high-performance renderer", Icons.Default.Bolt, renderer == Renderer.VULKAN) { renderer = Renderer.VULKAN } }
            item { Surface(color = Color(0xFF0F1116), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("Selected: ${renderer.name.lowercase().replaceFirstChar { it.uppercase() }}", Modifier.padding(14.dp), color = Color(0xFFBFC3CC), fontSize = 12.sp) } }
            item { SectionTitle("PERFORMANCE", "Basic graphics performance settings") }
            item { SettingSlider("Resolution Scale", resolution, "${resolution.roundToInt()}%", 50f..100f) { resolution = it } }
            item { SettingSlider("FPS Limit", fps, if (fps >= 240f) "Unlimited" else "${fps.roundToInt()} FPS", 30f..240f) { fps = it } }
            item { ToggleSetting("VSync", "Synchronize FPS with display", vsync) { vsync = it } }
            item { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).clickable { advanced = !advanced }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Tune, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("Advanced rendering", color = TextPrimary, fontWeight = FontWeight.Bold); Text("More graphics options", color = TextMuted, fontSize = 11.sp) }; Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextMuted) } }
            if (advanced) item { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(17.dp)) { AdvancedRow("Render Distance", "8 chunks"); AdvancedRow("Simulation Distance", "6 chunks"); AdvancedRow("Particles", "Decreased"); AdvancedRow("Clouds", "Fast"); AdvancedRow("Entity Shadows", "On") } }
            item { Button({ saved = true }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("SAVE RENDERING SETTINGS", fontWeight = FontWeight.Bold) } }
        }
    }
    if (saved) { LaunchedEffect(Unit) { kotlinx.coroutines.delay(1000); saved = false }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Surface(Modifier.padding(bottom = 30.dp), shape = RoundedCornerShape(14.dp), color = Card) { Text("Rendering settings saved (prototype)", Modifier.padding(18.dp), color = TextPrimary, fontSize = 12.sp) } } }
}

@Composable
private fun RendererOption(title: String, subtitle: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) CardSelected else Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(if (selected) Accent.copy(alpha = .18f) else Color(0xFF191C22), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) Accent else TextMuted) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 11.sp) }
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Accent))
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, valueText: String, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(valueText, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Slider(value, onValueChange = onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
    }
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 11.sp) }
        Switch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp)) { Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(subtitle, color = TextMuted, fontSize = 10.sp) }
}

@Composable
private fun AdvancedRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = Color(0xFFBFC3CC), fontSize = 12.sp); Text(value, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}
