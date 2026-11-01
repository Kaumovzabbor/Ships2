package com.friends.ships

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.friends.ships.ui.GameViewModel
import com.friends.ships.ui.Screen
import com.friends.ships.ui.BattleScreen
import com.friends.ships.ui.HomeScreen
import com.friends.ships.ui.HowToScreen
import com.friends.ships.ui.JoinScreen
import com.friends.ships.ui.LobbyScreen
import com.friends.ships.ui.OverScreen
import com.friends.ships.ui.PlaceScreen

private val Navy = Color(0xFF0B1F3A)
private val Teal = Color(0xFF26C6DA)

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Teal,
                    onPrimary = Navy,
                    background = Navy,
                    surface = Color(0xFF12305A),
                    onSurface = Color.White,
                    onBackground = Color.White,
                    secondary = Color(0xFF80DEEA),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val s by vm.state.collectAsState()
                    BackHandler(enabled = s.screen != Screen.HOME) {
                        if (s.screen == Screen.JOIN || s.screen == Screen.HOWTO) vm.go(Screen.HOME) else vm.leave()
                    }
                    when (s.screen) {
                        Screen.HOME -> HomeScreen(s, vm)
                        Screen.JOIN -> JoinScreen(s, vm)
                        Screen.LOBBY -> LobbyScreen(s, vm)
                        Screen.PLACE -> PlaceScreen(s, vm)
                        Screen.BATTLE -> BattleScreen(s, vm)
                        Screen.OVER -> OverScreen(s, vm)
                        Screen.HOWTO -> HowToScreen(vm)
                    }
                }
            }
        }
    }
}
