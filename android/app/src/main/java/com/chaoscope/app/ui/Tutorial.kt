package com.chaoscope.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaoscope.R

data class TutorialStepData(
    val targetRect: Rect?,
    val title: String,
    val body: String,
    val nextLabel: String,
)

/** Spotlight overlay with step-by-step coach marks. */
@Composable
fun TutorialOverlay(
    step: Int,
    anchors: com.chaoscope.TutorialAnchors,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    // Built here (not as a top-level constant) so the copy is pulled from the
    // localized string resources for the active language.
    val nextLabel = stringResource(R.string.tutorial_next)
    val steps = listOf(
        TutorialStepData(null,
            title     = stringResource(R.string.tutorial_welcome_title),
            body      = stringResource(R.string.tutorial_welcome_body),
            nextLabel = nextLabel,
        ),
        TutorialStepData(null,
            title     = stringResource(R.string.tutorial_pick_title),
            body      = stringResource(R.string.tutorial_pick_body),
            nextLabel = nextLabel,
        ),
        TutorialStepData(null,
            title     = stringResource(R.string.tutorial_tune_title),
            body      = stringResource(R.string.tutorial_tune_body),
            nextLabel = nextLabel,
        ),
        TutorialStepData(null,
            title     = stringResource(R.string.tutorial_render_title),
            body      = stringResource(R.string.tutorial_render_body),
            nextLabel = nextLabel,
        ),
        TutorialStepData(null,
            title     = stringResource(R.string.tutorial_palette_title),
            body      = stringResource(R.string.tutorial_palette_body),
            nextLabel = stringResource(R.string.tutorial_done),
        ),
    )
    val stepData = steps.getOrNull(step) ?: return

    // Resolve the spotlight rect for the current step
    val spotlightRect: Rect? = when (step) {
        0 -> anchors.canvas
        1 -> anchors.attractorRow
        2 -> anchors.paramSlider
        3 -> anchors.renderButton
        4 -> anchors.paletteRow
        else -> null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Consume all taps so underlying content isn't clickable
            .pointerInput(Unit) { detectTapGestures { /* consume */ } },
    ) {
        // Place the tooltip on the opposite half from the highlighted control so
        // the card never covers what it's pointing at. Targets in the lower half
        // (the control panel) push the card to the top.
        val placeCardAtTop = spotlightRect != null &&
            spotlightRect.center.y > constraints.maxHeight / 2f
        // The anchor rects are measured a frame after first composition. Until the
        // current step's anchor resolves we don't yet know which half to place the
        // card on, so we keep it invisible and fade it in once `placeCardAtTop` is
        // settled — otherwise the card visibly jumps from bottom to top on load.
        val cardReady = spotlightRect != null
        val cardAlpha by animateFloatAsState(
            targetValue   = if (cardReady) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
            label         = "tutorialCardFade",
        )
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
            repeat(steps.size) { i ->
                val alpha = if (i == step) 1f else 0.35f
                Surface(
                    modifier = Modifier.size(if (i == step) 8.dp else 6.dp),
                    shape    = RoundedCornerShape(50),
                    color    = Color(0xFF4FC3F7).copy(alpha = alpha),
                ) {}
            }
        }

        // ── Tooltip card (flips to the opposite half from the target) ────────
        Card(
            modifier = Modifier
                .align(if (placeCardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .alpha(cardAlpha)
                .then(if (placeCardAtTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                // Clear the progress dots when sitting at the top.
                .padding(top = if (placeCardAtTop) 28.dp else 0.dp)
                // Never let the card grow past the screen — otherwise a long
                // (translated) body can push the Skip/Next row off-screen and
                // the tutorial becomes impossible to dismiss.
                .heightIn(max = maxHeight * 0.7f)
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
                    // Body scrolls if it's too tall; the button row below stays
                    // pinned and always reachable.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text  = stringResource(R.string.tutorial_skip),
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
