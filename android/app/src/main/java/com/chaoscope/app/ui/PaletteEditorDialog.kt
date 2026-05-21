package com.chaoscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaoscope.ColorStop
import com.chaoscope.hsvToRgb
import com.chaoscope.rgbToHsv

private val Cyan = Color(0xFF4FC3F7)

@Composable
fun PaletteEditorDialog(
    initialStops: List<ColorStop>,
    onSave:       (List<ColorStop>) -> Unit,
    onDismiss:    () -> Unit,
) {
    var stops       by remember { mutableStateOf(initialStops.sortedBy { it.pos }) }
    var selectedIdx by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape          = RoundedCornerShape(20.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier            = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text       = "Custom Palette",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = Cyan,
                )

                // ── Live gradient preview ────────────────────────────────────
                GradientPreview(stops)

                // ── Colour stop row ──────────────────────────────────────────
                Text(
                    "Colour stops",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(stops) { idx, stop ->
                        val canRemove = stops.size > 2
                        StopSwatch(
                            stop     = stop,
                            selected = idx == selectedIdx,
                            onClick  = { selectedIdx = idx },
                            onRemove = if (canRemove) {
                                {
                                    val newStops = stops.toMutableList().also { it.removeAt(idx) }
                                    stops = newStops
                                    if (selectedIdx >= newStops.size) selectedIdx = newStops.size - 1
                                }
                            } else null,
                        )
                    }
                    if (stops.size < 8) {
                        item {
                            AddStopButton {
                                val lo = stops[stops.size - 2].pos
                                val hi = stops.last().pos
                                val newPos = (lo + hi) / 2f
                                val newStop = ColorStop(newPos, 0.5f, 0.5f, 0.5f)
                                val newStops = (stops + newStop).sortedBy { it.pos }
                                stops = newStops
                                selectedIdx = newStops.indexOfFirst { it.pos == newPos }.coerceAtLeast(0)
                            }
                        }
                    }
                }

                // ── Selected stop editor ─────────────────────────────────────
                stops.getOrNull(selectedIdx)?.let { sel ->
                    val minPos = if (selectedIdx > 0) stops[selectedIdx - 1].pos + 0.01f else 0f
                    val maxPos = if (selectedIdx < stops.size - 1) stops[selectedIdx + 1].pos - 0.01f else 1f
                    StopEditor(
                        stop     = sel,
                        minPos   = minPos,
                        maxPos   = maxPos,
                        onChange = { updated ->
                            stops = stops.toMutableList().also { it[selectedIdx] = updated }
                        },
                    )
                }

                // ── Buttons ──────────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick  = { onSave(stops.sortedBy { it.pos }) },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Gradient preview ──────────────────────────────────────────────────────────

@Composable
private fun GradientPreview(stops: List<ColorStop>) {
    val sorted = remember(stops) { stops.sortedBy { it.pos } }
    val brushColors = remember(sorted) { sorted.map { Color(it.r, it.g, it.b) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (brushColors.size >= 2)
                    Brush.horizontalGradient(brushColors)
                else
                    Brush.horizontalGradient(listOf(Color.Black, Color.White))
            ),
    )
}

// ── Stop swatch ───────────────────────────────────────────────────────────────

@Composable
private fun StopSwatch(
    stop:     ColorStop,
    selected: Boolean,
    onClick:  () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Box(modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(stop.r, stop.g, stop.b))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Cyan else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onClick),
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFCC4444))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Add-stop button ───────────────────────────────────────────────────────────

@Composable
private fun AddStopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = MaterialTheme.typography.titleMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Stop editor ───────────────────────────────────────────────────────────────

@Composable
private fun StopEditor(
    stop:     ColorStop,
    minPos:   Float,
    maxPos:   Float,
    onChange: (ColorStop) -> Unit,
) {
    val hsv = remember(stop.r, stop.g, stop.b) { rgbToHsv(stop.r, stop.g, stop.b) }
    var hue by remember(stop) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(stop) { mutableFloatStateOf(hsv[1]) }
    var bri by remember(stop) { mutableFloatStateOf(hsv[2]) }

    fun commit() {
        val (r, g, b) = hsvToRgb(hue, sat, bri)
        onChange(stop.copy(r = r, g = g, b = b))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(stop.r, stop.g, stop.b)),
            )
            Text(
                "H / S / V",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        SliderRow("H  ${"%.0f".format(hue)}°", hue / 360f,
            Brush.horizontalGradient(hueColors())) { hue = it * 360f; commit() }

        val satLo = hsvColor(hue, 0f, bri); val satHi = hsvColor(hue, 1f, bri)
        SliderRow("S  ${"%.0f".format(sat * 100)}%", sat,
            Brush.horizontalGradient(listOf(satLo, satHi))) { sat = it; commit() }

        val briHi = hsvColor(hue, sat, 1f)
        SliderRow("V  ${"%.0f".format(bri * 100)}%", bri,
            Brush.horizontalGradient(listOf(Color.Black, briHi))) { bri = it; commit() }

        // Position slider (only movable if there's room between neighbours)
        if (maxPos > minPos + 0.01f) {
            val posRange = (maxPos - minPos).coerceAtLeast(0.001f)
            SliderRow("Pos  ${"%.2f".format(stop.pos)}", (stop.pos - minPos) / posRange,
                null) { onChange(stop.copy(pos = minPos + it * posRange)) }
        }
    }
}

@Composable
private fun SliderRow(
    label:         String,
    value:         Float,
    track:         Brush?,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
             modifier = Modifier.width(76.dp),
             color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value         = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            modifier      = Modifier.weight(1f),
        )
    }
}

// ── Colour math (pure functions live in ColorMath.kt) ────────────────────────

private fun hsvColor(h: Float, s: Float, v: Float): Color {
    val (r, g, b) = hsvToRgb(h, s, v); return Color(r, g, b)
}

private fun hueColors(): List<Color> =
    (0..12).map { i -> hsvColor(i * 30f, 1f, 1f) }
