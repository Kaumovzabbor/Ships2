package com.friends.ships

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.friends.ships.ui.AppPrefs
import com.friends.ships.ui.BattleScreen
import com.friends.ships.ui.GameViewModel
import com.friends.ships.ui.HomeScreen
import com.friends.ships.ui.HowToScreen
import com.friends.ships.ui.JoinScreen
import com.friends.ships.ui.LobbyScreen
import com.friends.ships.ui.OverScreen
import com.friends.ships.ui.PlaceScreen
import com.friends.ships.ui.Screen
import com.friends.ships.ui.SettingsScreen
import java.util.Locale

private val Navy = Color(0xFF0B1F3A)
private val Teal = Color(0xFF26C6DA)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Navy,
    background = Navy,
    surface = Color(0xFF12305A),
    onSurface = Color.White,
    onBackground = Color.White,
    secondary = Color(0xFF80DEEA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    background = Color(0xFFEAF4F8),
    surface = Color.White,
    onSurface = Color(0xFF0B1F3A),
    onBackground = Color(0xFF0B1F3A),
    secondary = Color(0xFF006064),
)

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var startLang = ""

    /** Tanlangan tilni qo'llaydi (bo'sh bo'lsa tizim tili). */
    override fun attachBaseContext(base: Context) {
        val lang = AppPrefs(base).lang
        if (lang.isEmpty()) {
            super.attachBaseContext(base)
        } else {
            val cfg = Configuration(base.resources.configuration)
            cfg.setLocale(Locale(lang))
            super.attachBaseContext(base.createConfigurationContext(cfg))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startLang = AppPrefs(this).lang
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val s by vm.state.collectAsState()
            val dark = when (s.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(s.lang) { if (s.lang != startLang) recreate() }
            MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler(enabled = s.screen != Screen.HOME) {
                        when (s.screen) {
                            Screen.JOIN, Screen.HOWTO, Screen.SETTINGS -> vm.go(Screen.HOME)
                            else -> vm.leave()
                        }
                    }
                    when (s.screen) {
                        Screen.HOME -> HomeScreen(s, vm)
                        Screen.JOIN -> JoinScreen(s, vm)
                        Screen.LOBBY -> LobbyScreen(s, vm)
                        Screen.PLACE -> PlaceScreen(s, vm)
                        Screen.BATTLE -> BattleScreen(s, vm)
                        Screen.OVER -> OverScreen(s, vm)
                        Screen.HOWTO -> HowToScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(s, vm)
                    }
                }
            }
        }
    }
}
