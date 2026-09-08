package com.example.aetherlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aetherlauncher.minecraft.LoaderProfile
import com.example.aetherlauncher.minecraft.LoaderRepository
import com.example.aetherlauncher.minecraft.MinecraftRepository
import com.example.aetherlauncher.minecraft.MinecraftVersion
import com.example.aetherlauncher.minecraft.MinecraftLaunchEngine
import com.example.aetherlauncher.minecraft.mods.CurseForgeProvider
import com.example.aetherlauncher.minecraft.mods.ModInstaller
import com.example.aetherlauncher.minecraft.mods.ModProject
import com.example.aetherlauncher.minecraft.mods.ModProvider
import com.example.aetherlauncher.minecraft.mods.ModrinthProvider
import com.example.aetherlauncher.account.AccountType
import com.example.aetherlauncher.account.LauncherAccount
import com.example.aetherlauncher.account.LauncherAccountStore
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AetherTheme { AetherApp() } } }
}

@Composable private fun AetherTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Accent, onPrimary = Color.White, onBackground = TextPrimary, onSurface = TextPrimary), content = content)

@Composable private fun AetherApp() {
    val context = LocalContext.current
    val accountStore = remember { LauncherAccountStore(context) }
    val launchEngine = remember { MinecraftLaunchEngine(context) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedVersion by remember { mutableStateOf("1.21.8") }
    var selectedProfile by remember { mutableStateOf("Vanilla") }
    var toast by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf(accountStore.accounts()) }
    var selectedAccount by remember { mutableStateOf(accountStore.selected()) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }
    var launchProgress by remember { mutableStateOf("") }
    var launchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (accounts.isEmpty()) {
            val account = accountStore.addOfflineDemo("Offline account")
            accounts = accountStore.accounts()
            selectedAccount = account
        } else if (selectedAccount == null) selectedAccount = accounts.firstOrNull()
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    fun startLaunch() {
        if (launching) return
        val account = selectedAccount
        if (account == null) { toast = "Select an account first"; return }
        if (account.type != AccountType.OFFLINE_DEMO) { toast = "Microsoft authentication is not available yet"; return }
        launching = true; launchError = null; launchProgress = "Preparing Minecraft $selectedVersion…"
        scope.launch {
            launchEngine.installAndLaunchDemo(selectedVersion) { launchProgress = it }
                .onSuccess { launchProgress = "Minecraft process started"; launching = false; toast = "Minecraft started with Offline account" }
                .onFailure { launching = false; launchError = it.message ?: "Unable to launch Minecraft" }
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(selectedVersion, selectedProfile, { screen = Screen.VERSIONS }, { screen = Screen.MODS }, { screen = Screen.CONTROLS }, { screen = Screen.RENDERING }, { toast = it }, selectedAccount?.name ?: "No account", { showAccountDialog = true }, launching, launchProgress, launchError, { startLaunch() })
        Screen.VERSIONS -> VersionsScreen(selectedVersion, selectedProfile, { screen = Screen.HOME }) { version, profile -> selectedVersion = version; selectedProfile = profile; screen = Screen.HOME }
        Screen.MODS -> ModsScreen(selectedVersion) { screen = Screen.HOME }
        Screen.CONTROLS -> ControlsScreen { screen = Screen.HOME }
        Screen.RENDERING -> RenderingScreen { screen = Screen.HOME }
    }

    if (showAccountDialog) AccountDialog(accounts, selectedAccount, { accountStore.select(it.id); selectedAccount = it; showAccountDialog = false }, { showAccountDialog = false })

    toast?.let { message -> LaunchedEffect(message) { kotlinx.coroutines.delay(1900); toast = null }; Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Surface(Modifier.padding(bottom = 28.dp), color = Card, shape = RoundedCornerShape(14.dp)) { Text(message, Modifier.padding(16.dp), color = TextPrimary, fontSize = 12.sp) } } }
}

@Composable private fun HomeScreen(version: String, profile: String, onVersions: () -> Unit, onMods: () -> Unit, onControls: () -> Unit, onRendering: () -> Unit, toast: (String) -> Unit, accountName: String, onAccount: () -> Unit, launching: Boolean, launchProgress: String, launchError: String?, onPlay: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("AETHER", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("JAVA LAUNCHER", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp) }; IconButton(onClick = onAccount) { Icon(Icons.Default.Person, "Account", tint = TextPrimary) } }
        Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).clickable(onClick = onVersions).padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).background(Accent.copy(alpha = .22f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Gamepad, null, tint = Color.White, modifier = Modifier.size(30.dp)) }; Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text("Minecraft Java", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("$profile $version • $accountName", color = TextMuted, fontSize = 13.sp) }; Icon(Icons.Default.ChevronRight, null, tint = TextMuted) }; Spacer(Modifier.height(18.dp)); HorizontalDivider(color = Color.White.copy(alpha = .06f)); Spacer(Modifier.height(15.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { InfoItem(Icons.Default.Memory, "RAM", "2 GB"); InfoItem(Icons.Default.Speed, "FPS", "--"); InfoItem(Icons.Default.Bolt, "Renderer", "Auto") } }
        Button(
    onClick = onPlay,
    modifier = Modifier
        .fillMaxWidth()
        .height(66.dp),
    enabled = !launching,
    shape = RoundedCornerShape(20.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Accent)
) {
    if (launching) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
    } else {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }

    Spacer(Modifier.width(8.dp))

    Text(
        if (launching) "LAUNCHING" else "PLAY",
        fontSize = 19.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp
    )
}
    if (launching) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
    } else {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }

    Spacer(Modifier.width(8.dp))

    Text(
        if (launching) "LAUNCHING" else "PLAY",
        fontSize = 19.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp
    )
}

@Composable private fun AccountDialog(accounts: List<LauncherAccount>, selected: LauncherAccount?, onSelect: (LauncherAccount) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Accounts") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { account -> val isSelected = account.id == selected?.id; Surface(onClick = { onSelect(account) }, color = if (isSelected) CardSelected else Card, shape = RoundedCornerShape(14.dp)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (account.type == AccountType.OFFLINE_DEMO) Icons.Default.PersonOutline else Icons.Default.Person, null, tint = if (isSelected) Accent else TextMuted); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(account.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(if (account.type == AccountType.OFFLINE_DEMO) "Offline testing profile" else "Microsoft account", color = TextMuted, fontSize = 9.sp) }; if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Accent) } } }; Text("Offline account is for local launcher testing only. Full Minecraft ownership still requires Microsoft authentication.", color = TextMuted, fontSize = 9.sp) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Accent) } })
}

@Composable private fun InfoItem(icon: ImageVector, title: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp)); Spacer(Modifier.height(5.dp)); Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(title, color = TextMuted, fontSize = 10.sp) } }
@Composable private fun ToolCard(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) { Column(modifier.height(105.dp).background(Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = Accent, modifier = Modifier.size(23.dp)); Column { Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = TextMuted, fontSize = 10.sp) } } }

@Composable private fun VersionsScreen(selectedVersion: String, selectedProfile: String, onBack: () -> Unit, onSelect: (String, String) -> Unit) {
    val mc = remember { MinecraftRepository() }; val loaders = remember { LoaderRepository() }; val scope = rememberCoroutineScope()
    var vanilla by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }; var loaderVersions by remember { mutableStateOf<List<LoaderProfile>>(emptyList()) }; var profile by remember { mutableStateOf(selectedProfile) }; var gameVersion by remember { mutableStateOf(selectedVersion) }; var search by remember { mutableStateOf("") }; var stableOnly by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var menuOpen by remember { mutableStateOf(false) }
    fun loadVanilla() { scope.launch { loading = true; error = null; mc.fetchVersionManifest().onSuccess { vanilla = it.versions }.onFailure { error = it.message }; loading = false } }
    fun loadLoader() { scope.launch { loading = true; error = null; loaders.fetchProfiles(gameVersion).onSuccess { loaderVersions = it.filter { p -> p.loader == profile && (!stableOnly || p.stable) } }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(Unit) { loadVanilla() }; LaunchedEffect(profile, gameVersion, stableOnly) { if (profile != "Vanilla") loadLoader() }
    val filteredVanilla = vanilla.filter { it.id.contains(search, true) && (!stableOnly || it.type.equals("release", true)) }
    val filteredLoaders = loaderVersions.filter { it.loaderVersion.contains(search, true) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("VERSIONS", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Vanilla • Fabric • Forge • NeoForge • Quilt", color = TextMuted, fontSize = 10.sp) }; Box { IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Version filters", tint = TextPrimary) }; DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { DropdownMenuItem(text = { Text(if (stableOnly) "Show all versions" else "Stable versions only") }, onClick = { stableOnly = !stableOnly; menuOpen = false }); DropdownMenuItem(text = { Text("Refresh metadata") }, onClick = { menuOpen = false; if (profile == "Vanilla") loadVanilla() else loadLoader() }) } } }
        Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { listOf("Vanilla", "Fabric", "Forge", "NeoForge", "Quilt").forEach { option -> val color by animateColorAsState(if (profile == option) Accent else Card, tween(180), label = "profileColor"); Surface(onClick = { profile = option }, color = color, shape = RoundedCornerShape(12.dp)) { Text(option, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
        Spacer(Modifier.height(10.dp)); AnimatedVisibility(profile != "Vanilla", enter = fadeIn(), exit = fadeOut()) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text("Minecraft", color = TextMuted, fontSize = 10.sp); Spacer(Modifier.width(8.dp)); Text(gameVersion, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); TextButton(onClick = { val i = vanilla.indexOfFirst { it.id == gameVersion }; if (i >= 0 && i + 1 < vanilla.size) gameVersion = vanilla[i + 1].id }) { Text("CHANGE", color = Accent, fontSize = 10.sp) } } }
        Spacer(Modifier.height(10.dp)); OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search ${profile.lowercase()} versions...", color = TextMuted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
        Spacer(Modifier.height(10.dp)); AnimatedVisibility(stableOnly, enter = fadeIn(), exit = fadeOut()) { Surface(color = CardSelected, shape = RoundedCornerShape(12.dp)) { Text("✓ Stable versions only", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) } else if (error != null && (if (profile == "Vanilla") vanilla.isEmpty() else loaderVersions.isEmpty())) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CloudOff, null, tint = Accent, modifier = Modifier.size(42.dp)); Text(error ?: "Unable to load metadata", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); Button(onClick = { if (profile == "Vanilla") loadVanilla() else loadLoader() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("RETRY") } } } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) { if (profile == "Vanilla") items(filteredVanilla, key = { it.id }) { v -> VersionCard(v.id, "Official ${v.type}", v.id == selectedVersion, v.id == vanilla.firstOrNull()?.id) { onSelect(v.id, "Vanilla") } } else items(filteredLoaders, key = { "${it.loader}:${it.loaderVersion}" }) { p -> LoaderCard(p) { onSelect(p.gameVersion, p.loader) } } }
    }
}

@Composable private fun VersionCard(title: String, subtitle: String, selected: Boolean, latest: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(if (selected) CardSelected else Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Accent.copy(alpha = .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ViewInAr, null, tint = Accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp); if (latest) { Spacer(Modifier.width(7.dp)); Text("LATEST", color = Success, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }; Text(subtitle, color = TextMuted, fontSize = 10.sp) }; Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.ChevronRight, null, tint = if (selected) Accent else TextMuted) } }
@Composable private fun LoaderCard(p: LoaderProfile, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Accent.copy(alpha = .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = Accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(p.loaderVersion, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp); if (p.stable) { Spacer(Modifier.width(7.dp)); Text("STABLE", color = Success, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }; Text("${p.loader} • Minecraft ${p.gameVersion}", color = TextMuted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronRight, null, tint = TextMuted) } }

@Composable private fun ModsScreen(selectedVersion: String, onBack: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("aether_settings", 0) }; var apiKey by remember { mutableStateOf(prefs.getString("curseforge_key", "") ?: "") }; var providerName by remember { mutableStateOf("Modrinth") }; var showKey by remember { mutableStateOf(false) }
    val provider: ModProvider = remember(providerName, apiKey) { if (providerName == "CurseForge") CurseForgeProvider(apiKey) else ModrinthProvider() }; val installer = remember { ModInstaller(context) }; val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }; var loader by remember { mutableStateOf("Fabric") }; var results by remember { mutableStateOf<List<ModProject>>(emptyList()) }; var recommendations by remember { mutableStateOf<List<ModProject>>(emptyList()) }; var installed by remember { mutableStateOf(installer.installedMods()) }; var loading by remember { mutableStateOf(false) }; var installingId by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf<String?>(null) }
    fun loadRecommendations() { scope.launch { loading = true; provider.search("", selectedVersion, loader).onSuccess { recommendations = it }.onFailure { message = it.message ?: "Unable to load recommendations" }; loading = false } }
    fun searchMods() { scope.launch { loading = true; provider.search(query.trim(), selectedVersion, loader).onSuccess { results = it }.onFailure { message = it.message ?: "Provider search failed" }; loading = false } }
    LaunchedEffect(providerName, selectedVersion, loader) { results = emptyList(); loadRecommendations() }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Header("MODS", "$providerName • $selectedVersion", onBack); Spacer(Modifier.weight(1f)); IconButton(onClick = { showKey = true }) { Icon(Icons.Default.Key, "Provider key", tint = if (apiKey.isBlank()) TextMuted else Success) } }
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Modrinth", "CurseForge").forEach { option -> val c by animateColorAsState(if (providerName == option) Accent else Card, tween(160), label = "providerColor"); Surface(onClick = { providerName = option }, color = c, shape = RoundedCornerShape(12.dp)) { Text(option, Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
        Spacer(Modifier.height(9.dp)); OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search mods...", color = TextMuted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { searchMods() }) { Icon(Icons.Default.ArrowForward, "Search") } }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .08f), focusedContainerColor = Card, unfocusedContainerColor = Card, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Fabric", "Forge", "NeoForge", "Quilt", "Vanilla").forEach { option -> val c by animateColorAsState(if (loader == option) Accent else Card, tween(160), label = "modLoaderColor"); Surface(onClick = { loader = option }, color = c, shape = RoundedCornerShape(11.dp)) { Text(option, Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
        Spacer(Modifier.height(8.dp)); AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Card) }; AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) { message?.let { Surface(Modifier.padding(vertical = 6.dp), color = Card, shape = RoundedCornerShape(12.dp)) { Text(it, Modifier.padding(11.dp), color = TextMuted, fontSize = 10.sp) } } }
        Text(if (query.isBlank()) "Recommended for $selectedVersion" else "Search results", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 7.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) { items(if (query.isBlank()) recommendations else results, key = { "${it.provider}:${it.id}" }) { project -> ModCard(project, installingId == project.id) { scope.launch { installingId = project.id; provider.getCompatibleFile(project.id, selectedVersion, loader).fold({ file -> if (file == null || file.downloadUrl.isBlank()) message = "No compatible downloadable file found" else installer.installWithDependencies(file, provider, selectedVersion, loader).fold({ installed = installer.installedMods(); message = "Installed ${it.size} file(s)" }, { message = it.message ?: "Install failed" }) }, { message = it.message ?: "Unable to load mod file" }); installingId = null } } } }
        Text("Installed", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold); installed.take(4).forEach { file -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(file.name, Modifier.weight(1f), color = TextPrimary, fontSize = 10.sp); IconButton(onClick = { if (installer.remove(file)) installed = installer.installedMods() }) { Icon(Icons.Default.DeleteOutline, "Remove", tint = TextMuted) } } }
    }
    if (showKey) AlertDialog(onDismissRequest = { showKey = false }, title = { Text("CurseForge API key") }, text = { Column { Text("CurseForge's REST API requires an API key. It is stored only on this device.", color = TextMuted, fontSize = 11.sp); Spacer(Modifier.height(9.dp)); OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, singleLine = true, label = { Text("API key") }) } }, confirmButton = { TextButton(onClick = { prefs.edit().putString("curseforge_key", apiKey.trim()).apply(); showKey = false; if (apiKey.isNotBlank()) providerName = "CurseForge" }) { Text("SAVE", color = Accent) } }, dismissButton = { TextButton(onClick = { showKey = false }) { Text("CANCEL") } })
}

@Composable private fun ModCard(project: ModProject, installing: Boolean, onInstall: () -> Unit) { AnimatedVisibility(true, enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { 35 }, animationSpec = tween(200))) { Row(Modifier.fillMaxWidth().background(if (installing) CardSelected else Card, RoundedCornerShape(18.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = Accent) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(project.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(project.description, color = TextMuted, fontSize = 9.sp, maxLines = 2); Text("${project.downloads} downloads • ${project.provider.name}", color = TextMuted.copy(alpha = .7f), fontSize = 8.sp) }; Button(onClick = onInstall, enabled = !installing, contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { if (installing) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp) else Text("INSTALL", fontSize = 8.sp, fontWeight = FontWeight.Bold) } } } }

@Composable private fun ControlsScreen(onBack: () -> Unit) { var touch by remember { mutableStateOf(true) }; var vibration by remember { mutableStateOf(true) }; var haptic by remember { mutableStateOf(true) }; var opacity by remember { mutableFloatStateOf(.72f) }; var size by remember { mutableFloatStateOf(1f) }; SettingsPage("CONTROLS", "Touch + controller mapping", onBack) { SettingSwitch("Touch controls", "Show on-screen controls", touch) { touch = it }; SettingSwitch("Vibration", "Vibrate on touch buttons", vibration) { vibration = it }; SettingSwitch("Haptic feedback", "Feedback on button presses", haptic) { haptic = it }; SettingSlider("Button opacity", opacity, .25f..1f) { opacity = it }; SettingSlider("Button size", size, .7f..1.35f) { size = it }; SettingCard("Control layout", "Left movement • right look • action cluster", Icons.Default.Gamepad) } }
@Composable private fun RenderingScreen(onBack: () -> Unit) { var renderer by remember { mutableStateOf("Auto") }; var fps by remember { mutableFloatStateOf(60f) }; var resolution by remember { mutableFloatStateOf(100f) }; var clouds by remember { mutableStateOf(true) }; var particles by remember { mutableStateOf(true) }; var shadows by remember { mutableStateOf(true) }; var vsync by remember { mutableStateOf(true) }; SettingsPage("RENDERING", "Low-end friendly graphics controls", onBack) { SettingChoice("Renderer", listOf("Auto", "OpenGL", "Vulkan"), renderer) { renderer = it }; SettingSlider("FPS limit", fps, 20f..120f) { fps = it }; SettingSlider("Resolution scale", resolution, 50f..100f) { resolution = it }; SettingSwitch("VSync", "Synchronize frame output", vsync) { vsync = it }; SettingSwitch("Clouds", "Render Minecraft clouds", clouds) { clouds = it }; SettingSwitch("Particles", "Keep gameplay particles", particles) { particles = it }; SettingSwitch("Entity shadows", "Render mob/player shadows", shadows) { shadows = it }; SettingCard("Performance profile", "Balanced • Low • Battery saver", Icons.Default.Bolt) } }

@Composable private fun SettingsPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) { Header(title, subtitle, onBack); Spacer(Modifier.height(18.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } } } }
@Composable private fun SettingSwitch(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = TextMuted, fontSize = 9.sp) }; Switch(checked = value, onCheckedChange = onChange) } }
@Composable private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(if (range.endInclusive <= 1.5f) "${(value * 100).toInt()}%" else value.toInt().toString(), color = Accent, fontSize = 11.sp) }; Slider(value = value, onValueChange = onChange, valueRange = range) } }
@Composable private fun SettingChoice(title: String, options: List<String>, selected: String, onChange: (String) -> Unit) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { option -> Surface(onClick = { onChange(option) }, color = if (selected == option) Accent else CardSelected, shape = RoundedCornerShape(10.dp)) { Text(option, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = TextPrimary, fontSize = 10.sp) } } } } }
@Composable private fun SettingCard(title: String, subtitle: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Column { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = TextMuted, fontSize = 9.sp) } } }
@Composable private fun Header(title: String, subtitle: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }; Spacer(Modifier.width(14.dp)); Column { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text(subtitle, color = TextMuted, fontSize = 10.sp) } } }
