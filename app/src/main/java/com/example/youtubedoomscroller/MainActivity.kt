package com.example.youtubedoomscroller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.youtubedoomscroller.ui.theme.YoutubeDoomscrollerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YoutubeDoomscrollerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DoomscrollerSettings(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DoomscrollerSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("doomscroller_prefs", Context.MODE_PRIVATE) }
    
    var scrollDelay by remember { 
        mutableFloatStateOf(sharedPreferences.getFloat("scroll_delay", 10f)) 
    }
    
    // Check if voice was previously enabled AND we have permission
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var voiceEnabled by remember { 
        mutableStateOf(sharedPreferences.getBoolean("voice_enabled", false) && hasPermission) 
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        voiceEnabled = recordAudioGranted
        sharedPreferences.edit().putBoolean("voice_enabled", voiceEnabled).apply()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "YouTube Doomscroller Settings", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Auto-scroll Delay: ${scrollDelay.toInt()} seconds")
        Slider(
            value = scrollDelay,
            onValueChange = { 
                scrollDelay = it
                sharedPreferences.edit().putFloat("scroll_delay", it).apply()
            },
            valueRange = 5f..60f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Enable Voice Commands")
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = voiceEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        launcher.launch(permissions.toTypedArray())
                    } else {
                        voiceEnabled = false
                        sharedPreferences.edit().putBoolean("voice_enabled", false).apply()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Open Accessibility Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Note: You must enable 'Doomscroller Service' in Accessibility Settings for auto-scrolling to work.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
