package com.example.reconocedordinero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reconocedordinero.ui.help.HelpScreen
import com.example.reconocedordinero.ui.home.HomeScreen
import com.example.reconocedordinero.ui.result.ResultScreen
import com.example.reconocedordinero.ui.scan.ScanScreen
import com.example.reconocedordinero.ui.settings.SettingsScreen
import com.example.reconocedordinero.ui.theme.ReconocedorTheme

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val RESULT = "result"
    const val SETTINGS = "settings"
    const val HELP = "help"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ReconocedorApp).container
        setContent {
            ReconocedorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(container)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onScan = { navController.navigate(Routes.SCAN) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onHelp = { navController.navigate(Routes.HELP) },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                container = container,
                onResult = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
                onExit = { navController.popBackStack(Routes.HOME, false) },
            )
        }

        composable(Routes.RESULT) {
            ResultScreen(
                container = container,
                onBack = { navController.popBackStack(Routes.HOME, false) },
                onScanAgain = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.RESULT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HELP) {
            HelpScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
