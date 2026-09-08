package com.example.aetherlauncher.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aetherlauncher.MainActivity
import kotlinx.coroutines.launch

private val Bg = Color(0xFF08090C)
private val Card = Color(0xFF111318)
private val CardSelected = Color(0xFF1B1728)
private val Accent = Color(0xFF7C4DFF)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFF9296A1)
private val Success = Color(0xFF42D392)

class AccountActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AccountScreen() }
    }

    @Composable
    private fun AccountScreen() {
        val repository = remember { MicrosoftAuthRepository(this@AccountActivity) }
        val scope = rememberCoroutineScope()
        var account by remember { mutableStateOf(repository.currentAccount()) }
        var deviceCode by remember { mutableStateOf<MicrosoftAuthRepository.DeviceCode?>(null) }
        var loading by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        fun openLauncher() {
            startActivity(Intent(this@AccountActivity, MainActivity::class.java))
            finish()
        }

        LaunchedEffect(Unit) {
            if (account == null) {
                repository.restoreSession().onSuccess { account = it }.onFailure { message = it.message }
            }
        }

        fun startLogin() {
            scope.launch {
                loading = true
                message = null
                deviceCode = null
                repository.beginDeviceLogin().onSuccess { code ->
                    deviceCode = code
                    repository.completeDeviceLogin(code).onSuccess {
                        account = it
                        deviceCode = null
                    }.onFailure { message = it.message ?: "Microsoft sign-in failed" }
                }.onFailure { message = it.message ?: "Unable to start Microsoft sign-in" }
                loading = false
            }
        }

        Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }, modifier = Modifier.background(Card, RoundedCornerShape(14.dp))) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
                Spacer(Modifier.width(14.dp))
                Column { Text("ACCOUNT", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("Microsoft • Minecraft Java", color = TextMuted, fontSize = 11.sp) }
            }
            Spacer(Modifier.height(22.dp))

            if (account != null) {
                Surface(Modifier.fillMaxWidth(), color = Card, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).background(Accent.copy(alpha = .2f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Accent, modifier = Modifier.size(38.dp)) }
                        Spacer(Modifier.height(14.dp)); Text(account!!.minecraftName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Minecraft Java account connected", color = Success, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp)); Text(account!!.minecraftUuid, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center); Spacer(Modifier.height(18.dp))
                        Button(onClick = { openLauncher() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("OPEN AETHER LAUNCHER", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        Spacer(Modifier.height(9.dp))
                        OutlinedButton(onClick = { repository.signOut(); account = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(7.dp)); Text("SIGN OUT") }
                    }
                }
            } else {
                Surface(Modifier.fillMaxWidth(), color = Card, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).background(Accent.copy(alpha = .2f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountCircle, null, tint = Accent, modifier = Modifier.size(42.dp)) }
                        Spacer(Modifier.height(14.dp)); Text("Sign in with Microsoft", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp))
                        Text("Connect the Microsoft account that owns Minecraft Java Edition. Aether Launcher never asks for your Microsoft password.", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center); Spacer(Modifier.height(18.dp))
                        Button(onClick = { startLogin() }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text(if (loading) "WAITING FOR MICROSOFT..." else "SIGN IN", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }
            }

            deviceCode?.let { code ->
                Spacer(Modifier.height(14.dp)); Surface(Modifier.fillMaxWidth(), color = CardSelected, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ENTER THIS CODE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Spacer(Modifier.height(7.dp))
                        Text(code.userCode, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, modifier = Modifier.clickable { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft sign-in code", code.userCode)); message = "Code copied" })
                        Spacer(Modifier.height(5.dp)); Text("Tap the code to copy it", color = TextMuted, fontSize = 9.sp); Spacer(Modifier.height(12.dp))
                        Button(onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(7.dp)); Text("OPEN MICROSOFT") }
                        Spacer(Modifier.height(8.dp)); Text("Aether Launcher is waiting for the Microsoft login to finish.", color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            message?.let { error -> Spacer(Modifier.height(12.dp)); Surface(Modifier.fillMaxWidth(), color = Card, shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null, tint = Accent); Spacer(Modifier.width(9.dp)); Text(error, color = TextMuted, fontSize = 11.sp) } } }
            Spacer(Modifier.weight(1f)); Text("Tokens are stored using Android Keystore encryption.", Modifier.fillMaxWidth(), color = TextMuted.copy(alpha = .65f), fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}
