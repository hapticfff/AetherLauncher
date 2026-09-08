package com.example.aetherlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.aetherlauncher.account.AccountActivity
import com.example.aetherlauncher.account.MicrosoftAuthRepository

class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = MicrosoftAuthRepository(this).currentAccount()
        startActivity(Intent(this, if (account == null) AccountActivity::class.java else MainActivity::class.java))
        finish()
    }
}
