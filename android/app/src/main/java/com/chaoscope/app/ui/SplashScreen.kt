package com.chaoscope.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaoscope.AttractorType
import com.chaoscope.ChaoscopeEngine
import com.chaoscope.LANGUAGES
import com.chaoscope.LangPrefs
import com.chaoscope.PaletteType
import com.chaoscope.R
import com.chaoscope.RenderStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private val Primary   = Color(0xFF4FC3F7)
private val Secondary = Color(0xFF9C27B0)

@Composable
fun SplashScreen(
    onDismiss: () -> Unit,
    onShowTutorial: () -> Unit = {},
    isFirstLaunch: Boolean,
) {
    val context = LocalContext.current

    // ── Render the Lorenz butterfly in the background ─────────────────────────
    // Runs once on composition; fades in when complete.
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bgBitmap = withContext(Dispatchers.Default) {
            runCatching {
                val pixels = ChaoscopeEngine.nativeRender(
                    attractorType = AttractorType.LORENZ.ordinal,
                    params        = floatArrayOf(10f, 28f, 2.667f, 0.005f),
                    width         = 768,
                    height        = 768,
                    iterations    = 300_000L,
                    yaw           = 25f,
                    pitch         = 15f,
                    roll          = 0f,
                    zoom          = 1f,
                    paletteIndex  = PaletteType.NEBULA.ordinal,
                    gamma         = 1.0f,
                    renderStyle   = RenderStyle.STANDARD.ordinal,
                    bgColor       = 0x00000000,          // transparent — we draw over the splash bg
                    transparentBg = 1,
                ) ?: return@runCatching null
                Bitmap.createBitmap(pixels, 768, 768, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
    }

    val bgAlpha by animateFloatAsState(
        targetValue   = if (bgBitmap != null) 0.65f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label         = "bgFade",
    )

    // ── Animated ring overlay ─────────────────────────────────────────────────
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

        // ── Rendered attractor background ─────────────────────────────────
        bgBitmap?.let { bmp ->
            Image(
                bitmap             = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxSize()
                    .alpha(bgAlpha),
            )
        }

        // ── Bottom-up dark gradient so text stays readable ────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.30f to Color(0x22060610),
                            0.55f to Color(0xCC060610),
                            1.00f to Color(0xFF060610),
                        ),
                    ),
                ),
        )

        // ── Animated rings (subtle over the rendered background) ──────────
        Canvas(modifier = Modifier.size(300.dp)) {
            val cx = size.width  / 2f
            val cy = size.height / 2f

            for (i in 0 until 120) {
                val t     = i / 120f * 2f * Math.PI.toFloat()
                val r     = size.minDimension * 0.44f
                val x     = cx + r * cos(t)
                val y     = cy + r * sin(t)
                val alpha = (0.08f + 0.22f * ((sin(t * 3f + angle * 0.05f) + 1f) / 2f)) * pulse
                drawCircle(color = Primary.copy(alpha = alpha), radius = 1.5f,
                           center = Offset(x, y))
            }

            for (i in 0 until 80) {
                val t     = i / 80f * 2f * Math.PI.toFloat()
                val r     = size.minDimension * 0.28f
                val x     = cx + r * cos(t - Math.toRadians(angle.toDouble()).toFloat())
                val y     = cy + r * sin(t - Math.toRadians(angle.toDouble()).toFloat())
                val alpha = (0.10f + 0.18f * ((sin(t * 5f - angle * 0.08f) + 1f) / 2f)) * pulse
                drawCircle(color = Secondary.copy(alpha = alpha), radius = 1.2f,
                           center = Offset(x, y))
            }

            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(Primary.copy(alpha = 0.25f * pulse), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.15f,
                ),
                radius = size.minDimension * 0.15f,
                center = Offset(cx, cy),
            )
        }

        // ── Text + buttons ────────────────────────────────────────────────
        // Scrollable so longer translations (pt/fr/es run longer than English)
        // can't grow the column past the screen and push buttons out of view.
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(140.dp))

            Text(
                text          = "Chaoscope",
                fontSize      = 42.sp,
                fontWeight    = FontWeight.Bold,
                color         = Primary,
                letterSpacing = 2.sp,
            )
            Text(
                text          = stringResource(R.string.splash_tagline),
                fontSize      = 14.sp,
                color         = Secondary,
                letterSpacing = 1.sp,
            )
            Text(
                text          = "v${com.chaoscope.BuildConfig.VERSION_NAME}",
                fontSize      = 11.sp,
                color         = Color(0xFF6677AA),
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text       = stringResource(R.string.splash_description),
                fontSize   = 14.sp,
                color      = Color(0xFFBBBBCC),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
            )

            // Launch splash keeps a one-line tribute (most users never open
            // About); the About screen shows the full card.
            if (isFirstLaunch) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = stringResource(R.string.splash_tribute_short),
                    fontSize  = 11.sp,
                    color     = Color(0xFF8899BB),
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height(24.dp))

                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text       = stringResource(R.string.splash_tribute),
                        fontSize   = 12.sp,
                        color      = Color(0xFF8899BB),
                        textAlign  = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
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
                    text       = stringResource(
                        if (isFirstLaunch) R.string.splash_btn_explore else R.string.splash_btn_close
                    ),
                    color      = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                )
            }

            // Tutorial button: launch splash only — About dropped it now that
            // the settings sheet has "Replay tutorial".
            if (isFirstLaunch) {
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
                        text       = stringResource(R.string.splash_btn_tutorial),
                        color      = Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            // Feedback button lives on the About screen only.
            if (!isFirstLaunch) {
                Spacer(Modifier.height(12.dp))

                val feedbackSubject = stringResource(R.string.splash_feedback_subject)
                OutlinedButton(
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:chaoscope@duck.com")
                                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
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
                        text       = stringResource(R.string.splash_btn_feedback),
                        color      = Secondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Language selector ─────────────────────────────────────────
            var currentLang by remember { mutableStateOf(LangPrefs.get(context)) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 8.dp),
            ) {
                LANGUAGES.forEach { lang ->
                    val selected = currentLang == lang.code
                    Box(
                        modifier         = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Primary.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (lang.code != currentLang) {
                                    LangPrefs.set(context, lang.code)
                                    currentLang = lang.code
                                    (context as? Activity)?.recreate()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(lang.flag, fontSize = 20.sp)
                    }
                }
            }

            Text(
                text          = stringResource(R.string.splash_footer),
                fontSize      = 11.sp,
                color         = Color(0xFF445566),
                letterSpacing = 0.5.sp,
            )

            if (!isFirstLaunch) {
                Spacer(Modifier.height(6.dp))

                Text(
                    text          = stringResource(R.string.splash_credits),
                    fontSize      = 11.sp,
                    color         = Color(0xFF445566),
                    textAlign     = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // ── Skip / Close button ───────────────────────────────────────────
        // Composed after the scrollable column so it sits on top — composed
        // before it, the column's scroll area swallowed every tap on the ✕.
        IconButton(
            onClick  = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Close,
                contentDescription = stringResource(
                    if (isFirstLaunch) R.string.cd_skip_welcome else R.string.cd_close,
                ),
                tint               = Color(0xFF8899BB),
            )
        }
    }
}
