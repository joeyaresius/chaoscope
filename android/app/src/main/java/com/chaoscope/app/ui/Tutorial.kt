package com.chaoscope.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TutorialStepData(
    val targetRect: Rect?,
    val title: String,
    val body: String,
    val nextLabel: String,
)

private val STEPS = listOf(
    TutorialStepData(null,
        title     = "Welcome to Chaoscope",
        body      = "Drag anywhere on the canvas to rotate the attractor in 3D. Pinch to zoom.",
        nextLabel = "Next",
    ),
    TutorialStepData(null,
        title     = "Pick an attractor",
        body      = "Scroll the chip row to choose from 12 strange attractors. Each one has a unique shape.",
        nextLabel = "Next",
    ),
    TutorialStepData(null,
        title     = "Tune the parameters",
        body      = "Drag any slider to reshape the orbit in real time. Small changes can completely transform the picture.",
        nextLabel = "Next",
    ),
    TutorialStepData(null,
        title     = "Render in HD",
        body      = "Tap HD Render to run 50 million iterations and produce a full-resolution image.",
        nextLabel = "Next",
    ),
    TutorialStepData(null,
        title     = "Change the palette",
        body      = "Switch palette chips to recolour the attractor. Tap Edit to build your own gradient.",
        nextLabel = "Done",
    ),
)

/** Spotlight overlay with step-by-step coach marks. */
@Composable
fun TutorialOverlay(
    step: Int,
    anchors: com.chaoscope.TutorialAnchors,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val stepData = STEPS.getOrNull(step) ?: return

    // Resolve the spotlight rect for the current step
    val spotlightRect: Rect? = when (step) {
        0 -> anchors.canvas
        1 -> anchors.attractorRow
        2 -> anchors.paramSlider
        3 -> anchors.renderHdButton
        4 -> anchors.paletteRow
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Consume all taps so underlying content isn't clickable
            .pointerInput(Unit) { detectTapGestures { /* consume */ } },
    ) {
        // ── Semi-transparent overlay with cutout spotlight ──────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (spotlightRect != null) drawSpotlight(spotlightRect)
        }

        // ── Progress dots ───────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            repeat(STEPS.size) { i ->
                val alpha = if (i == step) 1f else 0.35f
                Surface(
                    modifier = Modifier.size(if (i == step) 8.dp else 6.dp),
                    shape    = RoundedCornerShape(50),
                    color    = Color(0xFF4FC3F7).copy(alpha = alpha),
                ) {}
            }
        }

        // ── Tooltip card ────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text       = stepData.title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF4FC3F7),
                )
                Text(
                    text  = stepData.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text  = "Skip tutorial",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    Button(
                        onClick = onNext,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4FC3F7),
                        ),
                        shape   = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text  = stepData.nextLabel,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSpotlight(rect: Rect) {
    val pad = 12.dp.toPx()
    drawRoundRect(
        color        = Color.Transparent,
        topLeft      = Offset(rect.left - pad, rect.top - pad),
        size         = Size(rect.width + pad * 2, rect.height + pad * 2),
        cornerRadius = CornerRadius(12.dp.toPx()),
        blendMode    = BlendMode.Clear,
    )
}
