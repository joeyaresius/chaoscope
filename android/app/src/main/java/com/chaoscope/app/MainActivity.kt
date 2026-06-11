package com.chaoscope

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
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
        // Edge-to-edge without androidx.activity.enableEdgeToEdge(), which on SDK 35
        // pulls in the deprecated Window.setStatusBarColor / setNavigationBarColor and
        // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES (flagged by the Play Console).
        // Bars are made transparent via the app theme (see themes.xml) for pre-35.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        setContent {
            ChaoscopeTheme {
                val splashDone   by vm.sessionSplashDone.collectAsStateWithLifecycle()
                var aboutVisible by remember { mutableStateOf(false) }

                when {
                    !splashDone -> {
                        SplashScreen(
                            onDismiss      = { vm.dismissSplash() },
                            onShowTutorial = {
                                // Force the tutorial even if it was dismissed in a
                                // past session (dismissSplash only auto-shows it once).
                                vm.dismissSplash()
                                vm.showTutorialAgain()
                            },
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
