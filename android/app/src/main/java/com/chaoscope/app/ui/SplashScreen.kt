package com.chaoscope.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaoscope.R
import kotlin.math.cos
import kotlin.math.sin

private val Primary   = Color(0xFF4FC3F7)
private val Secondary = Color(0xFF9C27B0)
private val BmcYellow = Color(0xFFFFDD00)

@Composable
fun SplashScreen(
    onDismiss: () -> Unit,
    onShowTutorial: () -> Unit = {},
    isFirstLaunch: Boolean,
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "rings")
    val angle by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = 360f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAngle",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610)),
        contentAlignment = Alignment.Center,
    ) {

        // ── Animated attractor rings (canvas) ─────────────────────────────
        Canvas(modifier = Modifier.size(300.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            for (i in 0 until 120) {
                val t  = i / 120f * 2f * Math.PI.toFloat()
                val r  = size.minDimension * 0.44f
                val x  = cx + r * cos(t)
                val y  = cy + r * sin(t)
                val alpha = (0.2f + 0.6f * ((sin(t * 3f + angle * 0.05f) + 1f) / 2f)) * pulse
                drawCircle(
                    color  = Primary.copy(alpha = alpha),
                    radius = 1.5f,
                    center = Offset(x, y),
                )
            }

            for (i in 0 until 80) {
                val t  = i / 80f * 2f * Math.PI.toFloat()
                val r  = size.minDimension * 0.28f
                val x  = cx + r * cos(t - Math.toRadians(angle.toDouble()).toFloat())
                val y  = cy + r * sin(t - Math.toRadians(angle.toDouble()).toFloat())
                val alpha = (0.3f + 0.5f * ((sin(t * 5f - angle * 0.08f) + 1f) / 2f)) * pulse
                drawCircle(
                    color  = Secondary.copy(alpha = alpha),
                    radius = 1.2f,
                    center = Offset(x, y),
                )
            }

            drawCircle(
                brush  = Brush.radialGradient(
                    colors  = listOf(
                        Primary.copy(alpha = 0.6f * pulse),
                        Color.Transparent,
                    ),
                    center  = Offset(cx, cy),
                    radius  = size.minDimension * 0.15f,
                ),
                radius = size.minDimension * 0.15f,
                center = Offset(cx, cy),
            )
        }

        // ── Top-right Skip / Close button ─────────────────────────────────
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Close,
                contentDescription = stringResource(
                    if (isFirstLaunch) R.string.cd_skip_welcome else R.string.cd_close
                ),
                tint               = Color(0xFF8899BB),
            )
        }

        // ── Text + buttons ────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(220.dp))

            Text(
                text       = "Chaoscope",
                fontSize   = 42.sp,
                fontWeight = FontWeight.Bold,
                color      = Primary,
                letterSpacing = 2.sp,
            )
            Text(
                text      = "Strange Attractor Explorer",
                fontSize  = 14.sp,
                color     = Secondary,
                letterSpacing = 1.sp,
            )
            Text(
                text      = "v0.1.0",
                fontSize  = 11.sp,
                color     = Color(0xFF6677AA),
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "Visualise the hidden order inside chaos.\n" +
                            "Millions of iterations. Twelve attractors.\n" +
                            "Infinite shapes waiting to be discovered.",
                fontSize  = 14.sp,
                color     = Color(0xFFBBBBCC),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text      = "✦  A tribute to the original Chaoscope\n" +
                                "by Nicolas Desprez (2000s) — the Windows app\n" +
                                "that first made strange attractors beautiful.",
                    fontSize  = 12.sp,
                    color     = Color(0xFF8899BB),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(
                    text       = if (isFirstLaunch) "Explore Attractors" else "Close",
                    color      = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                )
            }

            if (!isFirstLaunch) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick  = onShowTutorial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary),
                ) {
                    Text(
                        text       = "🎓  Show tutorial",
                        color      = Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://buymeacoffee.com/balancin"))
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BmcYellow),
            ) {
                Text(text = "☕  ", fontSize = 18.sp)
                Text(
                    text       = "Buy me a coffee",
                    color      = BmcYellow,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:chaoscope@duck.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Chaoscope — Suggestion / Feedback")
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Secondary),
            ) {
                Text(text = "✉  ", fontSize = 16.sp, color = Secondary)
                Text(
                    text       = "Suggest or Criticize",
                    color      = Secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text      = "Open source · Apache 2.0 License",
                fontSize  = 11.sp,
                color     = Color(0xFF445566),
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}
