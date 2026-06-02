package com.chaoscope.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.play.core.review.ReviewManagerFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoscope.*
import com.chaoscope.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ────────────────────────────────────────────────────────────────────────────
// 3-stop draggable panel
// ────────────────────────────────────────────────────────────────────────────

private enum class PanelStop { Collapsed, Quarter, Half }

// ────────────────────────────────────────────────────────────────────────────
// Root screen
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AttractorScreen(
    vm: ChaoscopeViewModel = viewModel(),
    onShowAbout: () -> Unit = {},
) {
    val state            by vm.uiState.collectAsStateWithLifecycle()
    val bucketedDots     by vm.bucketedDots.collectAsStateWithLifecycle()
    val paletteLut       by vm.paletteLut.collectAsStateWithLifecycle()
    val recentExports    by vm.recentExports.collectAsStateWithLifecycle()
    val showTutorial     by vm.showTutorial.collectAsStateWithLifecycle()
    val tutorialStep     by vm.tutorialStep.collectAsStateWithLifecycle()
    val tutorialAnchors  by vm.tutorialAnchors.collectAsStateWithLifecycle()
    val showPaletteEditor by vm.showPaletteEditor.collectAsStateWithLifecycle()
    val userPresets       by vm.userPresets.collectAsStateWithLifecycle()
    val context           = LocalContext.current
    val haptics           = LocalHapticFeedback.current

    // ── Notification permission (Android 13+) ────────────────────────────────
    // Without POST_NOTIFICATIONS the export foreground notification is silently
    // dropped.  We ask once on first launch; the user can deny — export still
    // works, they just won't see the progress notification or Cancel button.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied — no action needed; service handles missing permission */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── In-app review (fires once after 20 renders/exports) ──────────────────
    LaunchedEffect(Unit) {
        vm.reviewTrigger.collect {
            val activity = context as? Activity ?: return@collect
            val manager  = ReviewManagerFactory.create(context)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                        .addOnCompleteListener { /* review flow finished — nothing to do */ }
                }
            }
        }
    }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    // Non-null while the "video saved" dialog (Open / Share) is showing.
    var videoDoneUri by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val clipboard         = LocalClipboardManager.current

    // Auto-generated caption for the current attractor — rides along on shares
    // and the copy-to-clipboard action.
    val shareCaption    = buildShareCaption(state.attractorType, state.palette, state.params)
    val captionCopedMsg = stringResource(R.string.msg_caption_copied)

    val savedMsg        = stringResource(R.string.export_saved)
    val shareLabel      = stringResource(R.string.export_share)
    val shareChooser    = stringResource(R.string.share_render_chooser)
    val failedMsg       = state.exportError?.let { stringResource(R.string.export_failed, it) }
    val wallpaperOkMsg  = stringResource(R.string.msg_wallpaper_ok)
    val wallpaperErrMsg = state.wallpaperError?.let { stringResource(R.string.msg_wallpaper_failed, it) }
    val retryingMsg     = stringResource(R.string.msg_retrying)
    val videoFailedMsgFmt = state.videoExportError?.let { stringResource(R.string.msg_video_failed, it) }
    val shareVideoChooser = stringResource(R.string.share_video_chooser)

    // Surface export feedback as a Snackbar with View / Share actions.
    LaunchedEffect(state.exportDone, state.exportError) {
        when {
            failedMsg != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message  = failedMsg,
                    duration = SnackbarDuration.Short,
                )
                vm.clearExportFlag()
            }
            state.exportDone -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val uri = state.lastExportUri
                val result = snackbarHostState.showSnackbar(
                    message     = savedMsg,
                    actionLabel = if (uri != null) shareLabel else null,
                    duration    = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && uri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                        putExtra(Intent.EXTRA_TEXT, shareCaption)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, shareChooser))
                }
                vm.clearExportFlag()
            }
        }
    }

    // Render failed message snackbar.
    LaunchedEffect(state.renderFailedMessage) {
        val msg = state.renderFailedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        vm.clearRenderFailedMessage()
    }

    // Show "Retrying…" while the second attempt is in flight.
    LaunchedEffect(state.isRetrying) {
        if (state.isRetrying) {
            snackbarHostState.showSnackbar(
                message  = retryingMsg,
                duration = SnackbarDuration.Indefinite,
            )
        }
    }

    // Light haptic when a render completes (transition true → false).
    var wasRendering by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRendering) {
        if (wasRendering && !state.isRendering && state.bitmap != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasRendering = state.isRendering
    }

    // Surface video-export feedback as a Snackbar.
    LaunchedEffect(state.videoExportUri, state.videoExportError) {
        when {
            videoFailedMsgFmt != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message  = videoFailedMsgFmt,
                    duration = SnackbarDuration.Short,
                )
                vm.clearVideoExportFlag()
            }
            state.videoExportUri != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Show a completion dialog offering both Open and Share.
                videoDoneUri = state.videoExportUri
                vm.clearVideoExportFlag()
            }
        }
    }

    // Surface wallpaper feedback as a Snackbar.
    LaunchedEffect(state.wallpaperDone, state.wallpaperError) {
        when {
            wallpaperErrMsg != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message  = wallpaperErrMsg,
                    duration = SnackbarDuration.Short,
                )
                vm.clearWallpaperFlag()
            }
            state.wallpaperDone -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                    message  = wallpaperOkMsg,
                    duration = SnackbarDuration.Short,
                )
                vm.clearWallpaperFlag()
            }
        }
    }

    val density = LocalDensity.current
    val navBarHeightPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenH  = constraints.maxHeight.toFloat()
        val handlePx = with(density) { 28.dp.toPx() }

        val panelState = remember {
            AnchoredDraggableState(
                initialValue        = PanelStop.Quarter,
                positionalThreshold = { d -> d * 0.3f },
                velocityThreshold   = { with(density) { 125.dp.toPx() } },
                snapAnimationSpec   = spring(stiffness = Spring.StiffnessMediumLow),
                decayAnimationSpec  = exponentialDecay(),
            )
        }

        LaunchedEffect(screenH, navBarHeightPx) {
            panelState.updateAnchors(DraggableAnchors {
                PanelStop.Collapsed at screenH - handlePx - navBarHeightPx
                PanelStop.Quarter   at screenH * 0.75f
                PanelStop.Half      at screenH * 0.50f
            })
        }

        val offsetPx        = if (panelState.offset.isNaN()) screenH * 0.75f else panelState.offset
        val panelHeightDp   = with(density) { (screenH - offsetPx).toDp() }
        val contentHeightDp = (panelHeightDp - 28.dp).coerceAtLeast(0.dp)
        // ── Attractor canvas ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = panelHeightDp)
                // Background is handled by ThemedBackground as first child so themed
                // procedural art shows through the transparent attractor bitmap.
                .onGloballyPositioned { coords ->
                    vm.updateTutorialAnchor(TutorialTarget.Canvas, coords.boundsInWindow())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        if (zoomFactor != 1f) vm.zoomBy(zoomFactor)
                        else vm.rotateBy(pan.x * 0.4f, pan.y * 0.4f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Background layer — solid colour or procedural theme art
            ThemedBackground(
                bgColor      = state.bgColor,
                customBgArgb = state.customBgArgb,
                modifier     = Modifier.fillMaxSize(),
            )

            // Loading state — shown while GPU probe + dot preview are initialising
            if (bucketedDots == null && state.bitmap == null && !state.isRendering) {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    ChaosSpinner(palette = paletteLut, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text  = "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }

            if (bucketedDots != null) {
                val data = bucketedDots!!
                val livePreviewCd = stringResource(
                    R.string.cd_live_preview, state.attractorType.displayName
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = livePreviewCd },
                ) {
                    val halfW   = size.width  / 2f
                    val halfH   = size.height / 2f
                    val stroke  = 1.0.dp.toPx()
                    val paint   = android.graphics.Paint().apply {
                        isAntiAlias = true
                        strokeCap   = android.graphics.Paint.Cap.ROUND
                        strokeWidth = stroke
                    }
                    drawIntoCanvas { canvas ->
                        val nCanvas = canvas.nativeCanvas
                        for (b in data.buckets.indices) {
                            val src = data.buckets[b]
                            if (src.isEmpty()) continue
                            val dst = FloatArray(src.size)
                            var j = 0
                            while (j < src.size) {
                                dst[j]     = halfW + src[j]     * halfW
                                dst[j + 1] = halfH + src[j + 1] * halfH
                                j += 2
                            }
                            paint.color = data.colors[b]
                            nCanvas.drawPoints(dst, paint)
                        }
                    }
                }
            } else if (state.bitmap != null) {
                Image(
                    bitmap             = state.bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_attractor, state.attractorType.displayName),
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit,
                )
            }

            // Top-right: rendering spinner + cancel; otherwise About.
            Row(
                modifier              = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.isRendering) {
                    ChaosSpinner(
                        palette  = paletteLut,
                        modifier = Modifier.size(26.dp),
                    )
                    IconButton(onClick = vm::cancelRender) {
                        Icon(
                            imageVector        = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_cancel_render),
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(onClick = onShowAbout) {
                        Icon(
                            imageVector        = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.cd_about),
                            tint               = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // ── Three-stop bottom panel ───────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeightDp)
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .anchoredDraggable(panelState, Orientation.Vertical),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape          = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column {
                BottomSheetDragHandle()
                ControlPanel(
                    state              = state,
                    recentExports      = recentExports,
                    onAttractor        = vm::setAttractorType,
                    onApplyPreset      = vm::applyPreset,
                    userPresets        = userPresets,
                    onSavePreset       = { showSavePresetDialog = true },
                    onDeletePreset     = vm::deleteUserPreset,
                    onParam            = vm::updateParam,
                    onPalette          = vm::setPalette,
                    onYaw              = { vm.setCamera(yaw   = it) },
                    onPitch            = { vm.setCamera(pitch = it) },
                    onRoll             = { vm.setCamera(roll  = it) },
                    onZoom             = { vm.setCamera(zoom  = it) },
                    onGamma            = vm::setGamma,
                    onDepthCue         = vm::setDepthCue,
                    onFullRange        = vm::setFullRange,
                    onRenderQuality    = vm::setRenderQuality,
                    onPreviewDensity   = vm::setPreviewDensity,
                    onRenderStyle      = vm::setRenderStyle,
                    onRender           = vm::renderPreview,
                    onRenderHD         = vm::renderHD,
                    onRenderHD4K       = vm::renderHD4K,
                    onExport           = { vm.exportPng(context) },
                    onSetWallpaper     = { vm.setWallpaper(context) },
                    onTransparentBg    = vm::setTransparentBg,
                    onAnimMode         = vm::setAnimMode,
                    onSetKeyframeA     = vm::setKeyframeA,
                    onSetKeyframeB     = vm::setKeyframeB,
                    onAnimFrames       = vm::setAnimFrames,
                    onAnimPingPong     = vm::setAnimPingPong,
                    onExportVideo      = { vm.exportVideo(context) },
                    onCancelVideoExport = vm::cancelVideoExport,
                    onRandomizeParams  = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.randomizeParams()
                    },
                    onRandomizeAll     = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.randomize()
                    },
                    onBgColor          = vm::setBgColor,
                    onCustomBgColor    = vm::setCustomBgColor,
                    onEditPalette      = vm::openPaletteEditor,
                    onTutorialAnchor   = { target, rect -> vm.updateTutorialAnchor(target, rect) },
                    onOpenRecent       = { uriString ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uriString), "image/png")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }
                    },
                    onShareRecent      = { uriString ->
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                        putExtra(Intent.EXTRA_TEXT, shareCaption)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    shareChooser,
                                )
                            )
                        }
                    },
                    onCopyCaption      = {
                        clipboard.setText(AnnotatedString(shareCaption))
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message  = captionCopedMsg,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    isRendering        = state.isRendering,
                    paletteLut         = paletteLut,
                    panelContentHeight = contentHeightDp,
                )
            }
        }

        // ── Snackbar host (above the bottom panel) ────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = panelHeightDp + 16.dp)
                .padding(horizontal = 16.dp),
        )

        // ── Tutorial overlay ──────────────────────────────────────────────────
        if (showTutorial) {
            TutorialOverlay(
                step    = tutorialStep,
                anchors = tutorialAnchors,
                onNext  = vm::advanceTutorial,
                onSkip  = vm::dismissTutorial,
            )
        }
    }

    // ── Palette editor dialog ─────────────────────────────────────────────────
    if (showPaletteEditor) {
        PaletteEditorDialog(
            initialStops = state.customStops,
            onSave       = { stops ->
                vm.saveCustomStops(stops)
                vm.closePaletteEditor()
            },
            onDismiss    = vm::closePaletteEditor,
        )
    }

    // ── Save-preset dialog ────────────────────────────────────────────────────
    if (showSavePresetDialog) {
        SavePresetDialog(
            defaultName = state.attractorType.displayName,
            onSave      = { name ->
                vm.saveCurrentAsPreset(name)
                showSavePresetDialog = false
            },
            onDismiss   = { showSavePresetDialog = false },
        )
    }

    // ── Video-export complete: offer both Open and Share ──────────────────────
    videoDoneUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { videoDoneUri = null },
            title   = { Text(stringResource(R.string.video_done_title)) },
            confirmButton = {
                TextButton(onClick = {
                    videoDoneUri = null
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(uri), "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    }
                }) { Text(stringResource(R.string.export_open)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    videoDoneUri = null
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                shareVideoChooser,
                            )
                        )
                    }
                }) { Text(stringResource(R.string.export_share)) }
            },
        )
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    onSave:      (String) -> Unit,
    onDismiss:   () -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(stringResource(R.string.save_preset_title)) },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                label         = { Text(stringResource(R.string.label_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

// ────────────────────────────────────────────────────────────────────────────
// Bottom sheet drag handle
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomSheetDragHandle() {
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 32.dp, height = 4.dp),
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            shape    = MaterialTheme.shapes.small,
        ) {}
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Control panel (inside bottom sheet)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ControlPanel(
    state:              UiState,
    recentExports:      List<String>,
    onAttractor:        (AttractorType) -> Unit,
    onApplyPreset:      (Preset)        -> Unit,
    userPresets:        List<Preset>,
    onSavePreset:       ()              -> Unit,
    onDeletePreset:     (String)        -> Unit,
    onParam:            (Int, Float)    -> Unit,
    onPalette:          (PaletteType)   -> Unit,
    onYaw:              (Float)         -> Unit,
    onPitch:            (Float)         -> Unit,
    onRoll:             (Float)         -> Unit,
    onZoom:             (Float)         -> Unit,
    onGamma:            (Float)         -> Unit,
    onDepthCue:         (Float)         -> Unit,
    onFullRange:        (Boolean)       -> Unit,
    onRenderQuality:    (RenderQuality) -> Unit,
    onPreviewDensity:   (PreviewDensity)-> Unit,
    onRenderStyle:      (RenderStyle)   -> Unit,
    onRender:           ()              -> Unit,
    onRenderHD:         ()              -> Unit,
    onRenderHD4K:       ()              -> Unit,
    onExport:           ()              -> Unit,
    onSetWallpaper:     ()              -> Unit,
    onTransparentBg:    (Boolean)       -> Unit,
    onAnimMode:         (AnimMode)      -> Unit,
    onSetKeyframeA:     ()              -> Unit,
    onSetKeyframeB:     ()              -> Unit,
    onAnimFrames:       (Int)           -> Unit,
    onAnimPingPong:     (Boolean)       -> Unit,
    onExportVideo:      ()              -> Unit,
    onCancelVideoExport:()              -> Unit,
    onRandomizeParams:  ()              -> Unit,
    onRandomizeAll:     ()              -> Unit,
    onBgColor:          (BgColor)       -> Unit,
    onCustomBgColor:    (Int)           -> Unit,
    onOpenRecent:       (String)        -> Unit,
    onShareRecent:      (String)        -> Unit,
    onCopyCaption:      ()              -> Unit,
    onEditPalette:      ()              -> Unit,
    onTutorialAnchor:   (TutorialTarget, Rect) -> Unit,
    isRendering:        Boolean,
    paletteLut:         IntArray,
    panelContentHeight: Dp,
) {
    // Tab selection — survives rotation via rememberSaveable, not persisted to UiState
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabTitles = listOf(
        stringResource(R.string.tab_shape),
        stringResource(R.string.tab_look),
        stringResource(R.string.tab_camera),
        stringResource(R.string.tab_export),
    )
    val tabIcons  = listOf(
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.Palette,
        Icons.Outlined.Cameraswitch,
        Icons.Outlined.Upload,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelContentHeight),
    ) {
        // ── Tab row split into two pairs with a central play/render button ───
        // [Shape · Look]  ▶  [Camera · Export] — the gap keeps the FAB clear of
        // the tab tap targets.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelTab(tabTitles[0], tabIcons[0], selectedTab == 0,
                         { selectedTab = 0 }, Modifier.weight(1f).fillMaxHeight())
                PanelTab(tabTitles[1], tabIcons[1], selectedTab == 1,
                         { selectedTab = 1 }, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(64.dp))   // clear slot for the centred FAB
                PanelTab(tabTitles[2], tabIcons[2], selectedTab == 2,
                         { selectedTab = 2 }, Modifier.weight(1f).fillMaxHeight())
                PanelTab(tabTitles[3], tabIcons[3], selectedTab == 3,
                         { selectedTab = 3 }, Modifier.weight(1f).fillMaxHeight())
            }
            FloatingActionButton(
                onClick        = onRender,
                modifier       = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .onGloballyPositioned { c ->
                        onTutorialAnchor(TutorialTarget.RenderButton, c.boundsInWindow())
                    },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (isRendering) {
                    ChaosSpinner(
                        palette  = paletteLut,
                        modifier = Modifier.size(30.dp),
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.btn_render),
                    )
                }
            }
        }
        HorizontalDivider()

        // ── Tab content ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState    = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier       = Modifier.weight(1f),
            label          = "tab_content",
        ) { tab: Int ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {

                    // ── SHAPE ─────────────────────────────────────────────────
                    0 -> {
                        // Attractor selector
                        InfoSection(
                            title       = stringResource(R.string.section_attractor),
                            description = state.attractorType.description,
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.onGloballyPositioned { c ->
                                    onTutorialAnchor(TutorialTarget.AttractorRow, c.boundsInWindow())
                                },
                            ) {
                                items(AttractorType.entries) { type ->
                                    FilterChip(
                                        selected = state.attractorType == type,
                                        onClick  = { onAttractor(type) },
                                        label    = {
                                            Text(type.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }

                        // Surprise me — random attractor + palette + params + camera.
                        // A zero-thought discovery entry point for new users; shows the
                        // dot preview (no render) so they can tap play when they like it.
                        Button(
                            onClick  = onRandomizeAll,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                            ),
                        ) {
                            Text(stringResource(R.string.btn_surprise_me),
                                 color = MaterialTheme.colorScheme.onSecondary)
                        }

                        // Curated presets
                        val presets = state.attractorType.presets
                        if (presets.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.section_presets))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presets) { preset ->
                                    PresetThumb(
                                        preset  = preset,
                                        onClick = { onApplyPreset(preset) },
                                    )
                                }
                            }
                        }

                        // User presets
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SectionLabel(stringResource(R.string.section_my_presets))
                            TextButton(onClick = onSavePreset) {
                                Text(stringResource(R.string.btn_save_preset),
                                     style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (userPresets.isEmpty()) {
                            Text(
                                text  = stringResource(R.string.my_presets_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(userPresets, key = { it.name }) { preset ->
                                    AssistChip(
                                        onClick      = { onApplyPreset(preset) },
                                        label        = {
                                            Text(preset.name,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector        = Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.cd_delete_preset, preset.name),
                                                modifier           = Modifier
                                                    .size(16.dp)
                                                    .clickable { onDeletePreset(preset.name) },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // Parameters
                        SectionLabel(stringResource(R.string.section_parameters))
                        state.attractorType.paramNames.forEachIndexed { idx, name ->
                            val range = state.attractorType.paramRanges[idx]
                            val value = state.params.getOrElse(idx) { 0f }
                            val hint  = state.attractorType.paramHints.getOrNull(idx)
                            LabelledSlider(
                                label         = "$name = ${"%.3f".format(value)}",
                                value         = value,
                                valueRange    = range,
                                hint          = hint,
                                onCommitValue = { onParam(idx, it) },
                                onValueChange = { onParam(idx, it) },
                                modifier      = if (idx == 0) Modifier.onGloballyPositioned { c ->
                                    onTutorialAnchor(TutorialTarget.ParamSlider, c.boundsInWindow())
                                } else Modifier,
                            )
                        }

                        // Shuffle just this attractor's parameters (contextual — sits
                        // with the sliders it acts on). "Surprise Me" up top randomizes
                        // everything including the attractor type.
                        Button(
                            onClick  = onRandomizeParams,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                            ),
                        ) {
                            Text(stringResource(R.string.btn_randomize_params),
                                 color = MaterialTheme.colorScheme.onSecondary)
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // ── LOOK ──────────────────────────────────────────────────
                    1 -> {
                        // Palette
                        InfoSection(
                            title       = stringResource(R.string.section_palette),
                            description = state.palette.description,
                            extraAction = {
                                Icon(
                                    imageVector        = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_palette),
                                    tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier           = Modifier
                                        .size(16.dp)
                                        .clickable(onClick = onEditPalette),
                                )
                            },
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.onGloballyPositioned { c ->
                                    onTutorialAnchor(TutorialTarget.PaletteRow, c.boundsInWindow())
                                },
                            ) {
                                items(PaletteType.entries) { palette ->
                                    FilterChip(
                                        selected = state.palette == palette,
                                        onClick  = { onPalette(palette) },
                                        label    = {
                                            Text(palette.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }

                        // Render style
                        InfoSection(
                            title       = stringResource(R.string.section_render_style),
                            description = state.renderStyle.description,
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(RenderStyle.entries) { style ->
                                    FilterChip(
                                        selected = state.renderStyle == style,
                                        onClick  = { onRenderStyle(style) },
                                        label    = {
                                            Text(style.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }

                        // Background colour
                        SectionLabel(stringResource(R.string.section_background))
                        var showCustomBgPicker by remember { mutableStateOf(false) }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(BgColor.entries) { bg ->
                                val swatchArgb = if (bg == BgColor.CUSTOM) state.customBgArgb else bg.argb
                                val bgCompose  = Color(swatchArgb.toLong() and 0xFFFFFFFFL)
                                FilterChip(
                                    selected = state.bgColor == bg,
                                    onClick  = {
                                        onBgColor(bg)
                                        if (bg == BgColor.CUSTOM) showCustomBgPicker = true
                                    },
                                    label    = {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (bg.isTheme) {
                                                // Star indicator for procedural backgrounds
                                                Text("✦",
                                                     style = MaterialTheme.typography.labelSmall,
                                                     color = Color(0xFF4FC3F7))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(
                                                            color = bgCompose,
                                                            shape = MaterialTheme.shapes.extraSmall,
                                                        )
                                                )
                                            }
                                            Text(bg.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                )
                            }
                        }

                        // Show edit icon when Custom is already selected
                        if (state.bgColor == BgColor.CUSTOM) {
                            TextButton(
                                onClick  = { showCustomBgPicker = true },
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_custom_bg),
                                    modifier           = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_edit_colour),
                                     style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (showCustomBgPicker) {
                            CustomBgColorDialog(
                                initialArgb = state.customBgArgb,
                                onConfirm   = { argb ->
                                    onCustomBgColor(argb)
                                    showCustomBgPicker = false
                                },
                                onDismiss   = { showCustomBgPicker = false },
                            )
                        }

                        // Tone mapping
                        SectionLabel(stringResource(R.string.section_tone_mapping))
                        LabelledSlider(
                            label         = stringResource(R.string.slider_gamma, "%.2f".format(state.gamma)),
                            value         = state.gamma,
                            valueRange    = 0.3f..2.0f,
                            hint          = stringResource(R.string.gamma_hint),
                            onCommitValue = onGamma,
                            onValueChange = onGamma,
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.toggle_full_range),
                                     style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text  = stringResource(R.string.toggle_full_range_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Switch(checked = state.fullRange, onCheckedChange = onFullRange)
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.toggle_transparent_bg),
                                     style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text  = stringResource(R.string.toggle_transparent_bg_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Switch(checked = state.transparentBg, onCheckedChange = onTransparentBg)
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // ── CAMERA ────────────────────────────────────────────────
                    2 -> {
                        if (state.attractorType.is3D) {
                            SectionLabel(stringResource(R.string.section_camera))
                            LabelledSlider(stringResource(R.string.slider_yaw, "%.0f".format(state.yaw)),
                                state.yaw,   -180f..180f, onValueChange = onYaw)
                            LabelledSlider(stringResource(R.string.slider_pitch, "%.0f".format(state.pitch)),
                                state.pitch, -90f..90f,   onValueChange = onPitch)
                            LabelledSlider(stringResource(R.string.slider_roll, "%.0f".format(state.roll)),
                                state.roll,  -180f..180f, onValueChange = onRoll)
                            LabelledSlider(stringResource(R.string.slider_zoom, "%.2f".format(state.zoom)),
                                state.zoom,  0.1f..5f,    onValueChange = onZoom)
                            LabelledSlider(
                                label         = stringResource(R.string.slider_depth, "%.0f".format(state.depthCue * 100)),
                                value         = state.depthCue,
                                valueRange    = 0f..1f,
                                hint          = stringResource(R.string.depth_hint),
                                onValueChange = onDepthCue,
                            )
                        } else {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text  = stringResource(R.string.camera_3d_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // ── EXPORT ────────────────────────────────────────────────
                    3 -> {
                        // Export-resolution renders. The quick preview render lives
                        // on the central play button in the tab bar.
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick  = onRenderHD,
                                enabled  = !isRendering,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { c ->
                                        onTutorialAnchor(TutorialTarget.RenderHdButton,
                                                         c.boundsInWindow())
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                            ) {
                                Text(stringResource(R.string.btn_render_hd),
                                     color = MaterialTheme.colorScheme.onTertiary)
                            }
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick  = onRenderHD4K,
                                enabled  = !isRendering,
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                            ) {
                                Text(stringResource(R.string.btn_render_4k),
                                     color = MaterialTheme.colorScheme.onTertiary)
                            }
                            OutlinedButton(
                                onClick  = onExport,
                                enabled  = state.bitmap != null && !isRendering,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.btn_export_png)) }
                        }
                        OutlinedButton(
                            onClick  = onSetWallpaper,
                            enabled  = state.bitmap != null && !isRendering,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.btn_set_wallpaper)) }
                        OutlinedButton(
                            onClick  = onCopyCaption,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.btn_copy_caption)) }

                        // Recent renders
                        if (recentExports.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.section_recent_renders))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentExports) { uri ->
                                    RecentThumb(
                                        uri    = uri,
                                        onOpen  = { onOpenRecent(uri) },
                                        onShare = { onShareRecent(uri) },
                                    )
                                }
                            }
                        }

                        // Animation export
                        AnimationSection(
                            state               = state,
                            onAnimMode          = onAnimMode,
                            onSetKeyframeA      = onSetKeyframeA,
                            onSetKeyframeB      = onSetKeyframeB,
                            onAnimFrames        = onAnimFrames,
                            onAnimPingPong      = onAnimPingPong,
                            onExportVideo       = onExportVideo,
                            onCancelVideoExport = onCancelVideoExport,
                        )

                        // Performance
                        InfoSection(
                            title       = stringResource(R.string.section_render_detail),
                            description = state.renderQuality.description,
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(RenderQuality.entries) { q ->
                                    FilterChip(
                                        selected = state.renderQuality == q,
                                        onClick  = { onRenderQuality(q) },
                                        label    = {
                                            Text(q.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }
                        InfoSection(
                            title       = stringResource(R.string.section_preview_density),
                            description = state.previewDensity.description,
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(PreviewDensity.entries) { d ->
                                    FilterChip(
                                        selected = state.previewDensity == d,
                                        onClick  = { onPreviewDensity(d) },
                                        label    = {
                                            Text(d.displayName,
                                                 style = MaterialTheme.typography.labelSmall)
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Reusable composables
// ────────────────────────────────────────────────────────────────────────────

/**
 * Chaos-themed loading indicator: a few bodies orbit the centre on distinct
 * Lissajous paths (different x/y frequencies → non-circular, chaotic-looking),
 * each trailing a short fading comet tail. Tinted by the active palette.
 */
@Composable
private fun ChaosSpinner(palette: IntArray, modifier: Modifier = Modifier) {
    val colors = remember(palette) {
        if (palette.isEmpty())
            listOf(Color(0xFF4FC3F7), Color(0xFF9C7BFF), Color(0xFFFF7BAC))
        else listOf(0.55f, 0.78f, 0.97f).map { f ->
            Color(palette[(f * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)])
        }
    }
    // radiusFactor, freqX, freqY, phase — distinct per body for a tangled orbit.
    val bodies = remember {
        listOf(
            floatArrayOf(0.92f, 2f, 3f, 0.0f),
            floatArrayOf(0.66f, 3f, 2f, 1.7f),
            floatArrayOf(0.44f, 5f, 4f, 3.4f),
        )
    }
    val tail = 5

    val transition = rememberInfiniteTransition(label = "chaos_spinner")
    val t by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier) {
        val cx   = size.width  / 2f
        val cy   = size.height / 2f
        val rMax = size.minDimension / 2f * 0.88f
        val dot  = size.minDimension * 0.12f
        bodies.forEachIndexed { i, b ->
            val (rf, fx, fy, phase) = b
            val color = colors[i % colors.size]
            for (k in 0 until tail) {
                val tt = t - k * 0.18f
                val x  = cx + rf * rMax * cos(fx * tt + phase)
                val y  = cy + rf * rMax * sin(fy * tt + phase)
                val frac = 1f - k.toFloat() / tail
                drawCircle(
                    color  = color.copy(alpha = frac),
                    radius = dot * (0.5f + 0.5f * frac),
                    center = Offset(x, y),
                )
            }
        }
    }
}

/** One tab in the split control bar: stacked icon + label, tinted by selection. */
@Composable
private fun PanelTab(
    title:    String,
    icon:     ImageVector,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier            = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/**
 * A tappable preset card: a rendered thumbnail with the preset name underneath.
 * The thumbnail is rendered + cached lazily by [PresetThumbnails]; a spinner
 * shows until it is ready.
 */
@Composable
private fun PresetThumb(preset: Preset, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(initialValue = null, preset) {
        value = PresetThumbnails.get(context, preset)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier         = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = preset.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Text(
            text     = preset.name,
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A SectionLabel with an info-icon toggle and optional extra action slot. */
@Composable
private fun InfoSection(
    title:       String,
    description: String,
    extraAction: (@Composable () -> Unit)? = null,
    content:     @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(title)
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.cd_show_info, title),
                tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier           = Modifier
                    .size(14.dp)
                    .clickable { expanded = !expanded },
            )
            if (extraAction != null) {
                Spacer(Modifier.width(4.dp))
                extraAction()
            }
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text     = description,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        content()
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Custom background colour picker dialog — H/S/V sliders + live preview
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomBgColorDialog(
    initialArgb: Int,
    onConfirm:   (Int) -> Unit,
    onDismiss:   () -> Unit,
) {
    val initR = ((initialArgb shr 16) and 0xFF) / 255f
    val initG = ((initialArgb shr  8) and 0xFF) / 255f
    val initB = ( initialArgb         and 0xFF) / 255f
    val initHsv = remember { rgbToHsv(initR, initG, initB) }

    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var bri by remember { mutableFloatStateOf(initHsv[2]) }

    fun currentArgb(): Int {
        val (r, g, b) = hsvToRgb(hue, sat, bri)
        val ri = (r * 255).toInt().coerceIn(0, 255)
        val gi = (g * 255).toInt().coerceIn(0, 255)
        val bi = (b * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_custom_bg)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            color = Color(currentArgb().toLong() and 0xFFFFFFFFL),
                            shape = MaterialTheme.shapes.medium,
                        )
                )

                SaturationValueBox(hue = hue, sat = sat, value = bri) { s, v ->
                    sat = s; bri = v
                }
                HueBar(hue = hue) { h -> hue = h }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb()) }) { Text(stringResource(R.string.btn_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

@Composable
private fun LabelledSlider(
    label:          String,
    value:          Float,
    valueRange:     ClosedFloatingPointRange<Float>,
    hint:           String?       = null,
    modifier:       Modifier      = Modifier,
    onCommitValue:  ((Float) -> Unit)? = null,   // if non-null, value text becomes tappable
    onValueChange:  (Float) -> Unit,
) {
    var hintVisible  by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }

    if (showTypeDialog && onCommitValue != null) {
        var editText by remember { mutableStateOf("%.4f".format(value).trimEnd('0').trimEnd('.')) }
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(label) },
            text  = {
                OutlinedTextField(
                    value           = editText,
                    onValueChange   = { editText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label           = {
                        Text(stringResource(
                            R.string.label_value_range,
                            valueRange.start.toString(),
                            valueRange.endInclusive.toString(),
                        ))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editText.toFloatOrNull()
                        ?.coerceIn(valueRange)
                        ?.let { onCommitValue(it) }
                    showTypeDialog = false
                }) { Text(stringResource(R.string.btn_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onCommitValue != null) {
                Icon(
                    imageVector        = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_type_value, label),
                    tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier           = Modifier
                        .size(14.dp)
                        .clickable { showTypeDialog = true },
                )
            }
            if (hint != null) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.cd_show_hint, label),
                    tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier           = Modifier
                        .size(14.dp)
                        .clickable { hintVisible = !hintVisible },
                )
            }
        }
        if (hint != null) {
            AnimatedVisibility(visible = hintVisible) {
                Text(
                    text     = hint,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            modifier      = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Recents thumbnail
// ────────────────────────────────────────────────────────────────────────────

// ────────────────────────────────────────────────────────────────────────────
// Animation export panel
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimationSection(
    state:               UiState,
    onAnimMode:          (AnimMode) -> Unit,
    onSetKeyframeA:      () -> Unit,
    onSetKeyframeB:      () -> Unit,
    onAnimFrames:        (Int)     -> Unit,
    onAnimPingPong:      (Boolean) -> Unit,
    onExportVideo:       () -> Unit,
    onCancelVideoExport: () -> Unit,
) {
    val frameOptions = listOf(15, 30, 60)
    val canExport = when (state.animMode) {
        AnimMode.MORPH        -> state.keyframeA != null && state.keyframeB != null && !state.isExportingVideo
        AnimMode.ORBIT_TRACE,
        AnimMode.PARAM_SWEEP  -> state.animFrames >= 2 && !state.isExportingVideo
    }
    val pingPongDesc = when (state.animMode) {
        AnimMode.MORPH       -> stringResource(R.string.anim_morph_pingpong_desc)
        AnimMode.ORBIT_TRACE -> stringResource(R.string.anim_orbit_pingpong_desc)
        AnimMode.PARAM_SWEEP -> stringResource(R.string.anim_sweep_pingpong_desc)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.section_animation_export))

        // ── Mode selector (3 chips) ───────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.animMode == AnimMode.MORPH,
                onClick  = { onAnimMode(AnimMode.MORPH) },
                enabled  = !state.isExportingVideo,
                modifier = Modifier.weight(1f),
                label    = { Text(stringResource(R.string.anim_morph), style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = state.animMode == AnimMode.ORBIT_TRACE,
                onClick  = { onAnimMode(AnimMode.ORBIT_TRACE) },
                enabled  = !state.isExportingVideo,
                modifier = Modifier.weight(1f),
                label    = { Text(stringResource(R.string.anim_orbit_trace), style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = state.animMode == AnimMode.PARAM_SWEEP,
                onClick  = { onAnimMode(AnimMode.PARAM_SWEEP) },
                enabled  = !state.isExportingVideo,
                modifier = Modifier.weight(1f),
                label    = { Text(stringResource(R.string.anim_sweep), style = MaterialTheme.typography.labelSmall) },
            )
        }

        // ── Mode-specific controls ────────────────────────────────────────
        when (state.animMode) {
            AnimMode.MORPH -> {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick  = onSetKeyframeA,
                        enabled  = !state.isExportingVideo,
                        modifier = Modifier.weight(1f),
                        colors   = if (state.keyframeA != null)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(stringResource(
                                if (state.keyframeA != null) R.string.anim_frame_a_done
                                else R.string.anim_set_frame_a),
                             style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick  = onSetKeyframeB,
                        enabled  = !state.isExportingVideo,
                        modifier = Modifier.weight(1f),
                        colors   = if (state.keyframeB != null)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(stringResource(
                                if (state.keyframeB != null) R.string.anim_frame_b_done
                                else R.string.anim_set_frame_b),
                             style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!canExport && !state.isExportingVideo) {
                    Text(
                        text  = stringResource(R.string.anim_morph_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            AnimMode.ORBIT_TRACE -> {
                Text(
                    text  = stringResource(R.string.anim_orbit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            AnimMode.PARAM_SWEEP -> {
                Text(
                    text  = stringResource(R.string.anim_sweep_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        // ── Frame count chips + custom input ──────────────────────────────
        var frameText by rememberSaveable { mutableStateOf(state.animFrames.toString()) }
        // Keep the text field in sync when a chip is tapped or state changes externally
        LaunchedEffect(state.animFrames) { frameText = state.animFrames.toString() }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text  = stringResource(R.string.label_frames),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            frameOptions.forEach { n ->
                FilterChip(
                    selected = state.animFrames == n,
                    onClick  = { onAnimFrames(n) },
                    enabled  = !state.isExportingVideo,
                    label    = { Text("$n", style = MaterialTheme.typography.labelSmall) },
                )
            }
            OutlinedTextField(
                value         = frameText,
                onValueChange = { raw ->
                    frameText = raw.filter { it.isDigit() }.take(4)
                    val n = frameText.toIntOrNull() ?: return@OutlinedTextField
                    if (n in 2..600) onAnimFrames(n)
                },
                enabled        = !state.isExportingVideo,
                singleLine     = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle      = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
                modifier       = Modifier.width(72.dp),
            )
        }

        // ── Ping-pong toggle ──────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = stringResource(R.string.toggle_pingpong),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = pingPongDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked         = state.animPingPong,
                onCheckedChange = onAnimPingPong,
                enabled         = !state.isExportingVideo,
            )
        }

        // ── Export button / progress ──────────────────────────────────────
        if (state.isExportingVideo) {
            val progress = if (state.videoExportTotal > 0)
                state.videoExportProgress.toFloat() / state.videoExportTotal else 0f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text  = stringResource(R.string.anim_exporting_frame,
                                           state.videoExportProgress, state.videoExportTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                LinearProgressIndicator(
                    progress  = { progress },
                    modifier  = Modifier.fillMaxWidth(),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                OutlinedButton(
                    onClick  = onCancelVideoExport,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.btn_cancel_export)) }
            }
        } else {
            Button(
                onClick  = onExportVideo,
                enabled  = canExport,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(stringResource(R.string.btn_export_video), color = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}

@Composable
private fun RecentThumb(
    uri:     String,
    onOpen:  () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 8 // recents only need ~256px max
                    }
                    BitmapFactory.decodeStream(input, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 72.dp)
            .clickable(onClick = onOpen),
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!,
                contentDescription = stringResource(R.string.cd_recent_open),
                modifier           = Modifier
                    .fillMaxSize()
                    .background(Color.Black, RoundedCornerShape(8.dp)),
                contentScale       = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp),
                    ),
            )
        }
        IconButton(
            onClick  = onShare,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.cd_recent_share),
                tint               = Color.White,
                modifier           = Modifier
                    .size(14.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
            )
        }
    }
}
