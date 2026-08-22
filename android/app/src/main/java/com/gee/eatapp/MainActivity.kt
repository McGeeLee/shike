package com.gee.eatapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gee.eatapp.data.LegacyDataMigrator
import com.gee.eatapp.ui.ShikeApp
import com.gee.eatapp.ui.ShikeViewModel
import com.gee.eatapp.ui.theme.ShikeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ShikeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.light(0x00000000, 0x00000000),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            ShikeTheme(dynamicColor = viewModel.uiState.settings.dynamicColorEnabled) {
                ShikeApp(viewModel)
            }
        }
        handleIntent(intent)
        if (viewModel.legacyMigrationNeeded()) {
            LegacyDataMigrator.read(this, viewModel::importLegacyData)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_MEAL -> viewModel.requestImageSource()
            ACTION_OPEN_SETTINGS -> viewModel.openSettings()
        }
    }

    private companion object {
        const val ACTION_ADD_MEAL = "com.gee.eatapp.action.ADD_MEAL"
        const val ACTION_OPEN_SETTINGS = "com.gee.eatapp.action.OPEN_SETTINGS"
    }
}
