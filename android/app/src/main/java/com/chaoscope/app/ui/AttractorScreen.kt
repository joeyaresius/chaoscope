package com.chaoscope.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoscope.*
import com.chaoscope.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    val dotPoints        by vm.dotPoints.collectAsStateWithLifecycle()
    val recentExports    by vm.recentExports.collectAsStateWithLifecycle()
    val showTutorial     by vm.showTutorial.collectAsStateWithLifecycle()
    val tutorialStep     by vm.tutorialStep.collectAsStateWithLifecycle()
    val tutorialAnchors  by vm.tutorialAnchors.collectAsStateWithLifecycle()
    val showPaletteEditor by vm.showPaletteEditor.collectAsStateWithLifecycle()
    val userPresets       by vm.userPresets.collectAsStateWithLifecycle()
    val context           = LocalContext.current
    val haptics           = LocalHapticFeedback.current

    var showSavePresetDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val savedMsg     = stringResource(R.string.export_saved)
    val shareLabel   = stringResource(R.string.export_share)
    val shareChooser = stringResource(R.string.share_render_chooser)
    val failedMsg    = state.exportError?.let { stringResource(R.string.export_failed, it) }

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
                message  = "Retrying with more iterations…",
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
        val canvasBg        = Color(state.bgColor.argb.toLong() and 0xFFFFFFFFL)

        // ── Attractor canvas ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = panelHeightDp)
                .background(canvasBg)
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
            if (dotPoints != null) {
                val pts = dotPoints!!
                val livePreviewCd = stringResource(
                    R.string.cd_live_preview, state.attractorType.displayName
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = livePreviewCd },
                ) {
                    val halfW = size.width  / 2f
                    val halfH = size.height / 2f
                    val offsets = ArrayList<Offset>(pts.size / 2)
                    var i = 0
                    while (i < pts.size - 1) {
                        offsets.add(Offset(
                            halfW + pts[i]     * halfW,
                            halfH + pts[i + 1] * halfH,
                        ))
                        i += 2
                    }
                    drawPoints(
                        points      = offsets,
                        pointMode   = PointMode.Points,
                        color       = Color(0xFF4FC3F7),
                        strokeWidth = 1.0.dp.toPx(),
                        cap         = StrokeCap.Round,
                    )
                }
            } else if (state.bitmap != null) {
                Image(
                    bitmap             = state.bitmap!!.asImageBitmap(),
                    contentDescription = "Strange Attractor — ${state.attractorType.displayName}",
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
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary,
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
                    onRenderStyle      = vm::setRenderStyle,
                    onRender           = vm::renderPreview,
                    onRenderHD         = vm::renderHD,
                    onExport           = { vm.exportPng(context) },
                    onRandomizeParams  = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.randomizeParams()
                    },
                    onRandomizeAll     = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.randomize()
                    },
                    onBgColor          = vm::setBgColor,
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
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share render",
                                )
                            )
                        }
                    },
                    isRendering        = state.isRendering,
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
        title   = { Text("Save preset") },
        text    = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                label         = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    onRenderStyle:      (RenderStyle)   -> Unit,
    onRender:           ()              -> Unit,
    onRenderHD:         ()              -> Unit,
    onExport:           ()              -> Unit,
    onRandomizeParams:  ()              -> Unit,
    onRandomizeAll:     ()              -> Unit,
    onBgColor:          (BgColor)       -> Unit,
    onOpenRecent:       (String)        -> Unit,
    onShareRecent:      (String)        -> Unit,
    onEditPalette:      ()              -> Unit,
    onTutorialAnchor:   (TutorialTarget, Rect) -> Unit,
    isRendering:        Boolean,
    panelContentHeight: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelContentHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── Action buttons ───────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick  = onRender,
                enabled  = !isRendering,
                modifier = Modifier.weight(1f),
            ) {
                Text("▶  Render")
            }
            Button(
                onClick  = onRenderHD,
                enabled  = !isRendering,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { c -> onTutorialAnchor(TutorialTarget.RenderHdButton, c.boundsInWindow()) },
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                Text("HD Render", color = MaterialTheme.colorScheme.onTertiary)
            }
            OutlinedButton(
                onClick  = onExport,
                enabled  = state.bitmap != null && !isRendering,
                modifier = Modifier.weight(1f),
            ) {
                Text("Export")
            }
        }
        // ── Randomize buttons ─────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick  = onRandomizeParams,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text("🎲 Params", color = MaterialTheme.colorScheme.onSecondary)
            }
            Button(
                onClick  = onRandomizeAll,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text("🎲 Attractor", color = MaterialTheme.colorScheme.onSecondary)
            }
        }

        // ── Recents ──────────────────────────────────────────────────────────
        if (recentExports.isNotEmpty()) {
            SectionLabel("Recent Renders")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentExports) { uri ->
                    RecentThumb(
                        uri      = uri,
                        onOpen   = { onOpenRecent(uri) },
                        onShare  = { onShareRecent(uri) },
                    )
                }
            }
        }

        // ── Attractor selection ──────────────────────────────────────────────
        InfoSection(
            title       = "Attractor",
            description = state.attractorType.description,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.onGloballyPositioned { c ->
                    onTutorialAnchor(TutorialTarget.AttractorRow, c.boundsInWindow())
                },
            ) {
                items(AttractorType.entries) { type ->
                    FilterChip(
                        selected = state.attractorType == type,
                        onClick  = { onAttractor(type) },
                        label    = {
                            Text(
                                text  = type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // ── Presets (for the current attractor) ──────────────────────────────
        val presets = state.attractorType.presets
        if (presets.isNotEmpty()) {
            SectionLabel("Presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(presets) { preset ->
                    AssistChip(
                        onClick = { onApplyPreset(preset) },
                        label   = {
                            Text(
                                text  = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // ── My Presets (user-saved) ──────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("My Presets")
            TextButton(onClick = onSavePreset) {
                Text("+ Save current", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (userPresets.isEmpty()) {
            Text(
                text  = "Save the current attractor, parameters, camera and look as a reusable preset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(userPresets, key = { it.name }) { preset ->
                    AssistChip(
                        onClick      = { onApplyPreset(preset) },
                        label        = {
                            Text(
                                text  = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector        = Icons.Outlined.Close,
                                contentDescription = "Delete preset ${preset.name}",
                                modifier           = Modifier
                                    .size(16.dp)
                                    .clickable { onDeletePreset(preset.name) },
                            )
                        },
                    )
                }
            }
        }

        // ── Palette selection ────────────────────────────────────────────────
        InfoSection(
            title       = "Palette",
            description = state.palette.description,
            extraAction = {
                Icon(
                    imageVector        = Icons.Outlined.Edit,
                    contentDescription = "Edit custom palette",
                    tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier           = Modifier
                        .size(16.dp)
                        .clickable(onClick = onEditPalette),
                )
            },
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier.onGloballyPositioned { c ->
                    onTutorialAnchor(TutorialTarget.PaletteRow, c.boundsInWindow())
                },
            ) {
                items(PaletteType.entries) { palette ->
                    FilterChip(
                        selected = state.palette == palette,
                        onClick  = { onPalette(palette) },
                        label    = {
                            Text(
                                text  = palette.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // ── Render style ─────────────────────────────────────────────────────
        InfoSection(
            title       = "Render Style",
            description = state.renderStyle.description,
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RenderStyle.entries) { style ->
                    FilterChip(
                        selected = state.renderStyle == style,
                        onClick  = { onRenderStyle(style) },
                        label    = {
                            Text(
                                text  = style.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        // ── Background colour ────────────────────────────────────────────────
        SectionLabel("Background")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BgColor.entries) { bg ->
                val bgCompose = Color(bg.argb.toLong() and 0xFFFFFFFFL)
                FilterChip(
                    selected = state.bgColor == bg,
                    onClick  = { onBgColor(bg) },
                    label    = {
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = bgCompose,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    )
                            )
                            Text(
                                text  = bg.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                )
            }
        }

        // ── Attractor parameters ─────────────────────────────────────────────
        SectionLabel("Parameters")
        state.attractorType.paramNames.forEachIndexed { idx, name ->
            val range = state.attractorType.paramRanges[idx]
            val value = state.params.getOrElse(idx) { 0f }
            val hint  = state.attractorType.paramHints.getOrNull(idx)
            LabelledSlider(
                label         = "$name = ${"%.3f".format(value)}",
                value         = value,
                valueRange    = range,
                hint          = hint,
                onValueChange = { onParam(idx, it) },
                modifier      = if (idx == 0) Modifier.onGloballyPositioned { c ->
                    onTutorialAnchor(TutorialTarget.ParamSlider, c.boundsInWindow())
                } else Modifier,
            )
        }

        // ── Camera (3-D attractors only) ─────────────────────────────────────
        if (state.attractorType.is3D) {
            SectionLabel("Camera")
            LabelledSlider("Yaw = ${"%.0f".format(state.yaw)}°",
                state.yaw,   -180f..180f, onValueChange = onYaw)
            LabelledSlider("Pitch = ${"%.0f".format(state.pitch)}°",
                state.pitch, -90f..90f,   onValueChange = onPitch)
            LabelledSlider("Roll = ${"%.0f".format(state.roll)}°",
                state.roll,  -180f..180f, onValueChange = onRoll)
            LabelledSlider("Zoom = ${"%.2f".format(state.zoom)}",
                state.zoom,  0.1f..5f,    onValueChange = onZoom)
        }

        // ── Tone mapping ─────────────────────────────────────────────────────
        SectionLabel("Tone Mapping")
        LabelledSlider(
            label         = "Gamma = ${"%.2f".format(state.gamma)}",
            value         = state.gamma,
            valueRange    = 0.3f..2.0f,
            hint          = "Gamma flattens (lower) or boosts (higher) the brightest regions.",
            onValueChange = onGamma,
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Reusable composables
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
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
                contentDescription = "Show info about $title",
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

@Composable
private fun LabelledSlider(
    label:         String,
    value:         Float,
    valueRange:    ClosedFloatingPointRange<Float>,
    hint:          String? = null,
    modifier:      Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    var hintVisible by remember { mutableStateOf(false) }
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
            if (hint != null) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = "Show hint for $label",
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
