package com.chaoscope

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaoscope.data.ChaoscopePreferences
import com.chaoscope.ui.theme.ChaoscopeTheme
import kotlinx.coroutines.launch

/**
 * Settings page for the live wallpaper, reachable from the system wallpaper
 * picker (android:settingsActivity) — what to show and how fast to spin.
 */
class WallpaperSettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LangPrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChaoscopeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WallpaperSettingsScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun WallpaperSettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { ChaoscopePreferences(context.applicationContext) }
    val scope   = rememberCoroutineScope()

    val source by prefs.wallpaperSource
        .collectAsState(initial = ChaoscopePreferences.WP_SOURCE_CURRENT)
    val savedSpeed by prefs.wallpaperSpeed
        .collectAsState(initial = ChaoscopePreferences.WP_DEFAULT_SPEED)

    // Slider edits stay local while dragging; persisted on release.
    var draggingSpeed by remember { mutableFloatStateOf(Float.NaN) }
    val speed = if (draggingSpeed.isNaN()) savedSpeed else draggingSpeed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = stringResource(R.string.wp_settings_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text  = stringResource(R.string.wp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Text(
            text  = stringResource(R.string.wp_source_label).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = source == ChaoscopePreferences.WP_SOURCE_CURRENT,
                onClick  = {
                    scope.launch {
                        prefs.setWallpaperSource(ChaoscopePreferences.WP_SOURCE_CURRENT)
                    }
                },
                label = { Text(stringResource(R.string.wp_source_current)) },
            )
            FilterChip(
                selected = source == ChaoscopePreferences.WP_SOURCE_DAILY,
                onClick  = {
                    scope.launch {
                        prefs.setWallpaperSource(ChaoscopePreferences.WP_SOURCE_DAILY)
                    }
                },
                label = { Text(stringResource(R.string.daily_title)) },
            )
        }

        Text(
            text  = stringResource(R.string.wp_speed_label).uppercase() +
                "  ·  %.1f°/s".format(speed),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value                = speed,
            onValueChange        = { draggingSpeed = it },
            onValueChangeFinished = {
                val final = draggingSpeed
                draggingSpeed = Float.NaN
                if (!final.isNaN()) scope.launch { prefs.setWallpaperSpeed(final) }
            },
            valueRange = 0.5f..8f,
            modifier   = Modifier.fillMaxWidth(),
        )

        Button(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.tutorial_done)) }
    }
}
