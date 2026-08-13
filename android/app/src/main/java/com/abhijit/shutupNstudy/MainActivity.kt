package com.abhijit.shutupNstudy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.Intent
import com.abhijit.shutupNstudy.data.FirebaseSync
import com.abhijit.shutupNstudy.service.TimerService
import com.abhijit.shutupNstudy.theme.ShutupStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        FirebaseSync.initialize(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val requestPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Notification permission granted or not
                }
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            ShutupStudyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_FOREGROUND
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            // Service not running or block starting
        }
    }

    override fun onStop() {
        super.onStop()
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_BACKGROUND
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            // Service not running or block starting
        }
    }
}
