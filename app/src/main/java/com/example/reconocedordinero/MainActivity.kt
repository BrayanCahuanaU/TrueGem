package com.example.reconocedordinero

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reconocedordinero.ui.accessibility.LocalButtonNavigationEnabled
import com.example.reconocedordinero.ui.accessibility.LocalCursorController
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

    private lateinit var container: AppContainer
    private lateinit var navController: NavHostController

    private val accessibilityManager: AccessibilityManager? by lazy {
        getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as ReconocedorApp).container
        aplicarModoCaptura(intent)
        setContent {
            ReconocedorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val nav = rememberNavController()
                    SideEffect { navController = nav }

                    val settings by container.settings.collectAsStateWithLifecycle()
                    val touchExploration = rememberTouchExplorationEnabled()
                    val buttonNavigation = settings.buttonNavigation && !touchExploration

                    CompositionLocalProvider(
                        LocalCursorController provides container.cursor,
                        LocalButtonNavigationEnabled provides buttonNavigation,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppNavHost(nav, container)
                            if (buttonNavigation) {
                                CursorTapOverlay(
                                    onTap = ::selectCursorItem,
                                    onDoubleTap = ::navigateBack,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        aplicarModoCaptura(intent)
    }

    /**
     * Modo de captura para el dataset, solo en builds de depuración:
     * `adb shell am start -n <paquete>/.MainActivity --ez capturar true`.
     */
    private fun aplicarModoCaptura(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CAPTURAR, false) == true) {
            container.captureStore.forzado = true
            Log.i("MainActivity", "Modo de captura activado por intent")
        }
    }

    /**
     * Los botones de volumen se consumen para mover el cursor. El botón de encendido no llega
     * a las apps en Android (lo gestiona el sistema), pero se maneja por si algún dispositivo
     * lo entrega.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isButtonNavigationActive()) return super.dispatchKeyEvent(event)
        if (container.cursor.items.isEmpty()) return super.dispatchKeyEvent(event)

        val keyCode = event.keyCode
        val handled = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_POWER
        if (!handled) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (container.cursor.adjusting) adjustCursor(1) else moveCursor(-1)
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (container.cursor.adjusting) adjustCursor(-1) else moveCursor(1)
                }
                KeyEvent.KEYCODE_POWER -> moveCursor(1)
            }
        }
        return true
    }

    private fun isButtonNavigationActive(): Boolean =
        ::container.isInitialized &&
            container.settings.value.buttonNavigation &&
            accessibilityManager?.isTouchExplorationEnabled != true

    private fun moveCursor(delta: Int) {
        val cursor = container.cursor
        val item = cursor.move(delta) ?: return
        container.hapticManager.tick()
        Log.d(TAG, "Mover $delta -> ${item.id} (${cursor.selectedIndex + 1}/${cursor.size})")
        val value = item.valueText?.invoke()
        val texto = if (value != null) {
            getString(
                R.string.cursor_position_value,
                item.label,
                value,
                cursor.selectedIndex + 1,
                cursor.size,
            )
        } else {
            getString(
                R.string.cursor_position,
                item.label,
                cursor.selectedIndex + 1,
                cursor.size,
            )
        }
        container.speechManager.speak(texto)
    }

    private fun adjustCursor(delta: Int) {
        val item = container.cursor.selectedItem ?: return
        val texto = if (delta > 0) item.onIncrease?.invoke() else item.onDecrease?.invoke()
        container.hapticManager.tick()
        Log.d(TAG, "Ajustar $delta ${item.id} -> $texto")
        if (texto != null) {
            container.speechManager.speak(
                getString(R.string.cursor_adjust_value, item.label, texto),
            )
        }
    }

    private fun selectCursorItem() {
        val cursor = container.cursor
        val item = cursor.selectedItem ?: return
        container.hapticManager.tick()
        Log.d(TAG, "Seleccionar ${item.id} (ajuste=${cursor.adjusting})")

        if (item.isAdjustable) {
            if (cursor.adjusting) {
                cursor.exitAdjust()
                container.speechManager.speak(
                    getString(R.string.cursor_adjust_end, item.label),
                )
            } else if (cursor.enterAdjust()) {
                val value = item.valueText?.invoke()
                val texto = if (value != null) {
                    getString(R.string.cursor_adjust_mode_value, item.label, value)
                } else {
                    getString(R.string.cursor_adjust_mode, item.label)
                }
                container.speechManager.speak(texto)
            }
            return
        }

        item.onSelect()
    }

    private fun navigateBack() {
        val navego = ::navController.isInitialized && navController.navigateUp()
        container.hapticManager.tick()
        Log.d(TAG, "Atrás (navegó=$navego)")
        container.speechManager.speak(
            getString(if (navego) R.string.cursor_back else R.string.cursor_home),
        )
    }

    private companion object {
        const val TAG = "ButtonNavigation"
        const val EXTRA_CAPTURAR = "capturar"
    }
}

/** Capa transparente que convierte un toque en selección y dos toques en "atrás". */
@Composable
private fun CursorTapOverlay(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { currentOnDoubleTap() },
                )
            },
    )
}

/** Con TalkBack activo se deja que TalkBack gestione el foco y los toques. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(manager) {
        mutableStateOf(manager?.isTouchExplorationEnabled == true)
    }

    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val listener = object : AccessibilityManager.TouchExplorationStateChangeListener {
            override fun onTouchExplorationStateChanged(isEnabled: Boolean) {
                enabled = isEnabled
            }
        }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }

    return enabled
}

@Composable
private fun AppNavHost(navController: NavHostController, container: AppContainer) {
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
