package com.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.opencode.mobile.ui.OpenCodeRoot
import com.opencode.mobile.ui.theme.OpenCodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenCodeApplication).container
        setContent {
            var settings by remember { mutableStateOf(container.settingsStore.load()) }

            OpenCodeTheme(themeMode = settings.themeMode) {
                OpenCodeRoot(
                    container = container,
                    hasApiKey = settings.hasApiKey,
                    onSettingsSaved = { settings = it },
                )
            }
        }
    }
}
