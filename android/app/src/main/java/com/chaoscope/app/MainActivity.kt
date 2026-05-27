package com.chaoscope

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaoscope.ui.AttractorScreen
import com.chaoscope.ui.SplashScreen
import com.chaoscope.ui.theme.ChaoscopeTheme

class MainActivity : ComponentActivity() {

    private val vm: ChaoscopeViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LangPrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChaoscopeTheme {
                val splashDone   by vm.sessionSplashDone.collectAsStateWithLifecycle()
                var aboutVisible by remember { mutableStateOf(false) }

                when {
                    !splashDone -> {
                        SplashScreen(
                            onDismiss      = { vm.dismissSplash() },
                            onShowTutorial = { vm.dismissSplash() }, // tutorial shown post-splash
                            isFirstLaunch  = true,
                        )
                    }
                    aboutVisible -> {
                        SplashScreen(
                            onDismiss      = { aboutVisible = false },
                            onShowTutorial = {
                                aboutVisible = false
                                vm.showTutorialAgain()
                            },
                            isFirstLaunch  = false,
                        )
                    }
                    else -> AttractorScreen(
                        vm          = vm,
                        onShowAbout = { aboutVisible = true },
                    )
                }
            }
        }
    }
}
