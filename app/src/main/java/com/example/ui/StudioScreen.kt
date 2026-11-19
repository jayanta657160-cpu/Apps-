package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.imaging.CleanupStroke
import com.example.imaging.LightingProfile
import com.example.imaging.ParticleArtOverlay
import com.example.imaging.TouchLightMote
import com.example.model.CuratedRecipes
import com.example.model.GradingRecipe
import com.example.model.ParticleStyle
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.ElevatedTealSurface
import com.example.ui.theme.HighTealSurface
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory
import com.example.viewmodel.AureliaViewModel
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    viewModel: AureliaViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.ensureDefaultSampleLoaded(context)
    }

    val activeRecipe by viewModel.activeRecipe.collectAsStateWithLifecycle()
    val originalBitmap by viewModel.originalStudioBitmap.collectAsStateWithLifecycle()
    val gradedBitmap by viewModel.gradedStudioBitmap.collectAsStateWithLifecycle()
    val lightingProfile by viewModel.studioLighting.collectAsStateWithLifecycle()
    val histogram by viewModel.studioHistogram.collectAsStateWithLifecycle()
    val sourceLabel by viewModel.studioSourceLabel.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingStudio.collectAsStateWithLifecycle()
    val isAppShareBusy by viewModel.isAppShareBusy.collectAsStateWithLifecycle()
    val localPresets by viewModel.localPresets.collectAsStateWithLifecycle()
    val canUndoCleanup by viewModel.canUndoCleanup.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadPickedPhoto(context, uri)
        }
    }

    val touchMotes = remember { mutableStateListOf<TouchLightMote>() }
    val pendingCleanupStrokes = remember { mutableStateListOf<CleanupStroke>() }
    var cleanupModeEnabled by remember { mutableStateOf(false) }
    var cleanupBrushSize by remember { mutableStateOf(CleanupBrushSize.MEDIUM) }
    var splitCompareEnabled by remember { mutableStateOf(true) }
    var splitRatio by remember { mutableFloatStateOf(0.55f) }
    var selectedControlTab by remember { mutableIntStateOf(0) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianTeal)
            .statusBarsPadding()
    ) {
        // Studio Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Color & Particle Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmIvory
                )
                Text(
                    text = "$sourceLabel · ${lightingProfile.detectedScene}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElevatedTealSurface)
                        .testTag("studio_import_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(id = R.string.import_from_gallery),
                        tint = WarmIvory
                    )
                }

                IconButton(
                    onClick = { viewModel.shareStudioPhoto(context, touchMotes.toList()) },
                    enabled = !isProcessing && originalBitmap != null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElevatedTealSurface)
                        .testTag("studio_share_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(id = R.string.share_photo),
                        tint = ChampagneGold
                    )
                }

                Button(
                    onClick = { viewModel.exportStudioPhoto(context, touchMotes.toList()) },
                    enabled = !isProcessing && originalBitmap != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = ObsidianTeal
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("studio_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(id = R.string.download_photo),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.download_photo),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 1-Click Studio Action Strip + Source Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = originalBitmap?.hasAlpha() == true,
                enabled = !isProcessing && originalBitmap != null,
                onClick = { viewModel.removeBackgroundOneClick() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Remove BG", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LuminousTeal,
                    selectedLabelColor = ObsidianTeal,
                    selectedLeadingIconColor = ObsidianTeal,
                    containerColor = ElevatedTealSurface,
                    labelColor = WarmIvory,
                    iconColor = LuminousTeal
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = !isProcessing && originalBitmap != null,
                    selected = originalBitmap?.hasAlpha() == true,
                    borderColor = LuminousTeal.copy(alpha = 0.65f),
                    selectedBorderColor = LuminousTeal
                ),
                modifier = Modifier.testTag("studio_remove_bg_1click_button")
            )

            FilterChip(
                selected = true,
                enabled = !isProcessing && originalBitmap != null,
                onClick = { viewModel.applyEffectOneClick() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Apply Effect", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ChampagneGold,
                    selectedLabelColor = ObsidianTeal,
                    selectedLeadingIconColor = ObsidianTeal
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = !isProcessing && originalBitmap != null,
                    selected = true,
                    selectedBorderColor = ChampagneGold
                ),
                modifier = Modifier.testTag("studio_apply_effect_1click_button")
            )

            if (canUndoCleanup) {
                FilterChip(
                    selected = false,
                    enabled = !isProcessing,
                    onClick = { viewModel.undoObjectCleanup() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Undo", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ElevatedTealSurface,
                        labelColor = ChampagneGold,
                        iconColor = ChampagneGold
                    ),
                    modifier = Modifier.testTag("studio_undo_1click_button")
                )
            }

            FilterChip(
                selected = sourceLabel == "Wedding Garden (Ref)",
                onClick = {
                    viewModel.loadReferenceSample(
                        context,
                        R.drawable.img_sample_wedding_garden,
                        "Wedding Garden (Ref)"
                    )
                },
                label = { Text("Garden Couple", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LuminousTeal,
                    selectedLabelColor = ObsidianTeal
                ),
                modifier = Modifier.testTag("studio_sample_garden")
            )
            FilterChip(
                selected = sourceLabel == "Golden Portrait (Ref)",
                onClick = {
                    viewModel.loadReferenceSample(
                        context,
                        R.drawable.img_sample_golden_portrait,
                        "Golden Portrait (Ref)"
                    )
                },
                label = { Text("Emerald Portrait", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LuminousTeal,
                    selectedLabelColor = ObsidianTeal
                ),
                modifier = Modifier.testTag("studio_sample_portrait")
            )
            FilterChip(
                selected = splitCompareEnabled && !cleanupModeEnabled,
                onClick = {
                    if (cleanupModeEnabled) {
                        cleanupModeEnabled = false
                        pendingCleanupStrokes.clear()
                    }
                    splitCompareEnabled = !splitCompareEnabled
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Before / After", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ChampagneGold,
                    selectedLabelColor = ObsidianTeal
                ),
                modifier = Modifier.testTag("studio_split_toggle")
            )
            FilterChip(
                selected = cleanupModeEnabled,
                onClick = {
                    cleanupModeEnabled = !cleanupModeEnabled
                    if (cleanupModeEnabled) {
                        splitCompareEnabled = false
                    } else {
                        pendingCleanupStrokes.clear()
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Remove Object", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF5A79).copy(alpha = 0.88f),
                    selectedLabelColor = ObsidianTeal
                ),
                modifier = Modifier.testTag("studio_cleanup_toggle")
            )
            FilterChip(
                selected = false,
                onClick = { showShareAppDialog = true },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.share_app),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ElevatedTealSurface,
                    labelColor = WarmIvory,
                    iconColor = ChampagneGold
                ),
                modifier = Modifier.testTag("studio_share_app_button")
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp)
        ) {
            val isWide = maxWidth >= 720.dp
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StudioPreviewPane(
                        originalBitmap = originalBitmap,
                        gradedBitmap = gradedBitmap,
                        recipe = activeRecipe,
                        lightingProfile = lightingProfile,
                        histogram = histogram,
                        isProcessing = isProcessing,
                        splitCompareEnabled = splitCompareEnabled && !cleanupModeEnabled,
                        splitRatio = splitRatio,
                        onSplitRatioChange = { splitRatio = it },
                        touchMotes = touchMotes,
                        cleanupModeEnabled = cleanupModeEnabled,
                        cleanupBrushSize = cleanupBrushSize,
                        onSelectBrushSize = { cleanupBrushSize = it },
                        pendingCleanupStrokes = pendingCleanupStrokes,
                        canUndoCleanup = canUndoCleanup,
                        onApplyCleanup = {
                            viewModel.applyObjectCleanup(pendingCleanupStrokes.toList())
                            pendingCleanupStrokes.clear()
                        },
                        onUndoCleanup = { viewModel.undoObjectCleanup() },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .padding(bottom = 12.dp)
                    )
                    StudioControlPanel(
                        recipe = activeRecipe,
                        selectedTab = selectedControlTab,
                        onSelectTab = { selectedControlTab = it },
                        onUpdateRecipe = viewModel::updateRecipe,
                        onApplyRecipe = { viewModel.applyRecipe(it) },
                        onResetRecipe = { viewModel.applyRecipe(CuratedRecipes.signature) },
                        onSavePresetClick = { showSavePresetDialog = true },
                        localPresets = localPresets,
                        onClearTouchMotes = { touchMotes.clear() },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioPreviewPane(
                        originalBitmap = originalBitmap,
                        gradedBitmap = gradedBitmap,
                        recipe = activeRecipe,
                        lightingProfile = lightingProfile,
                        histogram = histogram,
                        isProcessing = isProcessing,
                        splitCompareEnabled = splitCompareEnabled && !cleanupModeEnabled,
                        splitRatio = splitRatio,
                        onSplitRatioChange = { splitRatio = it },
                        touchMotes = touchMotes,
                        cleanupModeEnabled = cleanupModeEnabled,
                        cleanupBrushSize = cleanupBrushSize,
                        onSelectBrushSize = { cleanupBrushSize = it },
                        pendingCleanupStrokes = pendingCleanupStrokes,
                        canUndoCleanup = canUndoCleanup,
                        onApplyCleanup = {
                            viewModel.applyObjectCleanup(pendingCleanupStrokes.toList())
                            pendingCleanupStrokes.clear()
                        },
                        onUndoCleanup = { viewModel.undoObjectCleanup() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.48f)
                            .padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StudioControlPanel(
                        recipe = activeRecipe,
                        selectedTab = selectedControlTab,
                        onSelectTab = { selectedControlTab = it },
                        onUpdateRecipe = viewModel::updateRecipe,
                        onApplyRecipe = { viewModel.applyRecipe(it) },
                        onResetRecipe = { viewModel.applyRecipe(CuratedRecipes.signature) },
                        onSavePresetClick = { showSavePresetDialog = true },
                        localPresets = localPresets,
                        onClearTouchMotes = { touchMotes.clear() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.52f)
                    )
                }
            }
        }
    }

    if (showSavePresetDialog) {
        SavePresetDialog(
            initialName = activeRecipe.name,
            onDismiss = { showSavePresetDialog = false },
            onSave = { name, desc ->
                viewModel.savePersonalPreset(name, desc)
                showSavePresetDialog = false
            }
        )
    }

    if (showShareAppDialog) {
        ShareAppDialog(
            isBusy = isAppShareBusy,
            onDismiss = { showShareAppDialog = false },
            onShareApk = { viewModel.shareAppApk(context) },
            onDownloadApk = { viewModel.downloadAppApk(context) },
            onShareInvite = { viewModel.shareAppInvite(context) },
            onPermissionDenied = { msg -> viewModel.notifyUser(msg) }
        )
    }
}

@Composable
private fun StudioPreviewPane(
    originalBitmap: Bitmap?,
    gradedBitmap: Bitmap?,
    recipe: GradingRecipe,
    lightingProfile: LightingProfile,
    histogram: FloatArray,
    isProcessing: Boolean,
    splitCompareEnabled: Boolean,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    touchMotes: MutableList<TouchLightMote>,
    cleanupModeEnabled: Boolean,
    cleanupBrushSize: CleanupBrushSize,
    onSelectBrushSize: (CleanupBrushSize) -> Unit,
    pendingCleanupStrokes: MutableList<CleanupStroke>,
    canUndoCleanup: Boolean,
    onApplyCleanup: () -> Unit,
    onUndoCleanup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(DeepStudioTeal)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
        ) {
            val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val allowBrush = recipe.particleStyle == ParticleStyle.TOUCH_TRAIL &&
                !splitCompareEnabled && !cleanupModeEnabled

            val displayGraded = gradedBitmap ?: originalBitmap
            if (displayGraded != null) {
                if (displayGraded.hasAlpha()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellPx = 22.dp.toPx()
                        val cols = (size.width / cellPx).toInt() + 1
                        val rows = (size.height / cellPx).toInt() + 1
                        for (r in 0..rows) {
                            for (c in 0..cols) {
                                val isDark = (r + c) % 2 == 0
                                drawRect(
                                    color = if (isDark) Color(0xFF102428) else Color(0xFF19353B),
                                    topLeft = Offset(c * cellPx, r * cellPx),
                                    size = Size(cellPx, cellPx)
                                )
                            }
                        }
                    }
                }
                // Graded full image
                Image(
                    bitmap = displayGraded.asImageBitmap(),
                    contentDescription = "Teal & Warm graded photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live Particle Art Layer
            ParticleArtOverlay(
                recipe = recipe,
                touchMotes = touchMotes,
                allowTouchPaint = allowBrush,
                modifier = Modifier.fillMaxSize()
            )

            if (cleanupModeEnabled && displayGraded != null) {
                ObjectCleanupOverlay(
                    bitmap = displayGraded,
                    pendingStrokes = pendingCleanupStrokes,
                    brushSize = cleanupBrushSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Before/After Left Clip showing raw Original
            if (splitCompareEnabled && originalBitmap != null && gradedBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(splitRatio.coerceIn(0.05f, 0.95f))
                        .clipToBounds()
                        .background(DeepStudioTeal)
                ) {
                    if (originalBitmap.hasAlpha()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cellPx = 22.dp.toPx()
                            val cols = (size.width / cellPx).toInt() + 1
                            val rows = (size.height / cellPx).toInt() + 1
                            for (r in 0..rows) {
                                for (c in 0..cols) {
                                    val isDark = (r + c) % 2 == 0
                                    drawRect(
                                        color = if (isDark) Color(0xFF102428) else Color(0xFF19353B),
                                        topLeft = Offset(c * cellPx, r * cellPx),
                                        size = Size(cellPx, cellPx)
                                    )
                                }
                            }
                        }
                    }
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Original ungraded photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(this@BoxWithConstraints.maxWidth)
                    )
                }

                // Champagne gold divider line + draggable handle
                val offsetX = (containerWidthPx * splitRatio.coerceIn(0.05f, 0.95f)).roundToInt()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(offsetX - 24, 0) }
                        .width(48.dp)
                        .pointerInput(containerWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val updated = (splitRatio + dragAmount / containerWidthPx).coerceIn(0.06f, 0.94f)
                                onSplitRatioChange(updated)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(ChampagneGold)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ChampagneGold)
                            .border(2.dp, ObsidianTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Compare,
                            contentDescription = "Drag to compare original vs graded",
                            tint = ObsidianTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Corner Labels
                Surface(
                    color = ObsidianTeal.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "ORIGINAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmIvory,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = ObsidianTeal.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "TEAL & WARM",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChampagneGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (cleanupModeEnabled) {
                Surface(
                    color = Color(0xFFFF5A79).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "BRUSH UNWANTED OBJECT TO REMOVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianTeal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else if (allowBrush) {
                Surface(
                    color = LuminousTeal.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "DRAG PHOTO TO PAINT LIGHT PARTICLES",
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianTeal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (isProcessing) {
                Surface(
                    color = ObsidianTeal.copy(alpha = 0.75f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        color = ChampagneGold,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        if (cleanupModeEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            ObjectCleanupBar(
                brushSize = cleanupBrushSize,
                onSelectBrushSize = onSelectBrushSize,
                hasPendingStrokes = pendingCleanupStrokes.isNotEmpty(),
                canUndo = canUndoCleanup,
                isProcessing = isProcessing,
                onApply = onApplyCleanup,
                onClearPending = { pendingCleanupStrokes.clear() },
                onUndo = onUndoCleanup
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Luminance Scope & Preservation Telemetry Bar
        Surface(
            color = DeepStudioTeal,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LuminanceHistogramScope(
                    bins = histogram,
                    modifier = Modifier
                        .width(120.dp)
                        .height(30.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "BLACKS ${(lightingProfile.shadowP10 * 100).toInt()}% · WHITES ${(lightingProfile.highlightP95 * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "SKIN GUARD ${(recipe.skinProtection * 100).toInt()}% · NO GENERATIVE EDITS",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun LuminanceHistogramScope(
    bins: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bins.isEmpty()) return@Canvas
        val barWidth = size.width / bins.size
        for (i in bins.indices) {
            val v = bins[i].coerceIn(0.04f, 1f)
            val barH = v * size.height
            val color = when {
                i < bins.size / 3 -> LuminousTeal.copy(alpha = 0.8f)
                i > (bins.size * 2) / 3 -> ChampagneGold.copy(alpha = 0.85f)
                else -> WarmIvory.copy(alpha = 0.72f)
            }
            drawRect(
                color = color,
                topLeft = Offset(i * barWidth, size.height - barH),
                size = Size((barWidth - 1f).coerceAtLeast(1f), barH)
            )
        }
    }
}

@Composable
private fun StudioControlPanel(
    recipe: GradingRecipe,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onUpdateRecipe: ((GradingRecipe) -> GradingRecipe) -> Unit,
    onApplyRecipe: (GradingRecipe) -> Unit,
    onResetRecipe: () -> Unit,
    onSavePresetClick: () -> Unit,
    localPresets: List<com.example.data.local.LocalPresetEntity>,
    onClearTouchMotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DeepStudioTeal,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ChampagneGold,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = ChampagneGold
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    text = { Text("Color Grade", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("studio_tab_color")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    text = { Text("Particle Art", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("studio_tab_particles")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    text = { Text("Looks", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("studio_tab_looks")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Adapt Grading to Lighting",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WarmIvory
                                )
                                Text(
                                    text = "Auto white balance, exposure & highlight rolloff",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = recipe.adaptiveLighting,
                                onCheckedChange = { checked ->
                                    onUpdateRecipe { it.copy(adaptiveLighting = checked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianTeal,
                                    checkedTrackColor = ChampagneGold
                                ),
                                modifier = Modifier.testTag("studio_adaptive_switch")
                            )
                        }

                        GradingSlider(
                            label = "Teal Shadows",
                            value = recipe.tealShadows,
                            valueRange = 0f..1f,
                            accent = LuminousTeal,
                            onValueChange = { v -> onUpdateRecipe { it.copy(tealShadows = v) } },
                            testTag = "slider_teal_shadows"
                        )
                        GradingSlider(
                            label = "Warm Golden Highlights",
                            value = recipe.warmHighlights,
                            valueRange = 0f..1f,
                            accent = ChampagneGold,
                            onValueChange = { v -> onUpdateRecipe { it.copy(warmHighlights = v) } },
                            testTag = "slider_warm_highlights"
                        )
                        GradingSlider(
                            label = "Deep Foliage Greens",
                            value = recipe.deepGreens,
                            valueRange = 0f..1f,
                            accent = LuminousTeal,
                            onValueChange = { v -> onUpdateRecipe { it.copy(deepGreens = v) } },
                            testTag = "slider_deep_greens"
                        )
                        GradingSlider(
                            label = "Skin Tone Protection",
                            value = recipe.skinProtection,
                            valueRange = 0f..1f,
                            accent = ChampagneGold,
                            onValueChange = { v -> onUpdateRecipe { it.copy(skinProtection = v) } },
                            testTag = "slider_skin_protection"
                        )
                        GradingSlider(
                            label = "Contrast & Depth",
                            value = recipe.contrast,
                            valueRange = 0f..1f,
                            accent = WarmIvory,
                            onValueChange = { v -> onUpdateRecipe { it.copy(contrast = v) } },
                            testTag = "slider_contrast"
                        )
                        GradingSlider(
                            label = "Exposure",
                            value = recipe.exposure,
                            valueRange = -0.65f..0.65f,
                            accent = WarmIvory,
                            isSigned = true,
                            onValueChange = { v -> onUpdateRecipe { it.copy(exposure = v) } },
                            testTag = "slider_exposure"
                        )
                        GradingSlider(
                            label = "Soft Highlight Rolloff",
                            value = recipe.highlightSoftness,
                            valueRange = 0f..1f,
                            accent = ChampagneGold,
                            onValueChange = { v -> onUpdateRecipe { it.copy(highlightSoftness = v) } },
                            testTag = "slider_highlight_softness"
                        )
                        GradingSlider(
                            label = "Skin-Safe Vibrance",
                            value = recipe.vibrance,
                            valueRange = 0f..1f,
                            accent = LuminousTeal,
                            onValueChange = { v -> onUpdateRecipe { it.copy(vibrance = v) } },
                            testTag = "slider_vibrance"
                        )
                    }

                    1 -> {
                        Text(
                            text = "Particle Art Atmosphere",
                            style = MaterialTheme.typography.titleMedium,
                            color = WarmIvory
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ParticleStyle.entries.forEach { style ->
                                val selected = recipe.particleStyle == style
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdateRecipe { it.copy(particleStyle = style) } },
                                    label = { Text(style.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LuminousTeal,
                                        selectedLabelColor = ObsidianTeal,
                                        containerColor = ElevatedTealSurface
                                    ),
                                    modifier = Modifier.testTag("studio_particle_${style.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = recipe.particleStyle.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChampagneGold
                        )

                        GradingSlider(
                            label = "Particle Density",
                            value = recipe.particleDensity,
                            valueRange = 0f..1f,
                            accent = LuminousTeal,
                            onValueChange = { v -> onUpdateRecipe { it.copy(particleDensity = v) } },
                            testTag = "slider_particle_density"
                        )
                        GradingSlider(
                            label = "Bokeh Glow",
                            value = recipe.particleGlow,
                            valueRange = 0f..1f,
                            accent = ChampagneGold,
                            onValueChange = { v -> onUpdateRecipe { it.copy(particleGlow = v) } },
                            testTag = "slider_particle_glow"
                        )
                        GradingSlider(
                            label = "Drift Speed",
                            value = recipe.particleSpeed,
                            valueRange = 0f..1f,
                            accent = WarmIvory,
                            onValueChange = { v -> onUpdateRecipe { it.copy(particleSpeed = v) } },
                            testTag = "slider_particle_speed"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include Particles on Export",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WarmIvory
                                )
                                Text(
                                    text = "Turn off to export pure untouched DSLR color grade",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = recipe.bakeParticlesOnExport,
                                onCheckedChange = { checked ->
                                    onUpdateRecipe { it.copy(bakeParticlesOnExport = checked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianTeal,
                                    checkedTrackColor = LuminousTeal
                                ),
                                modifier = Modifier.testTag("studio_bake_particles_switch")
                            )
                        }

                        if (recipe.particleStyle == ParticleStyle.TOUCH_TRAIL) {
                            OutlinedButton(
                                onClick = onClearTouchMotes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("studio_clear_particles_button")
                            ) {
                                Text("Clear Painted Particles")
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSavePresetClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChampagneGold,
                                    contentColor = ObsidianTeal
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("studio_save_preset_button")
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(id = R.string.save_preset))
                            }
                            OutlinedButton(
                                onClick = onResetRecipe,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("studio_reset_recipe_button")
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset to Classic")
                            }
                        }

                        Text(
                            text = "Signature Wedding Recipes",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChampagneGold
                        )
                        CuratedRecipes.all.forEach { preset ->
                            RecipeSummaryCard(
                                title = preset.name,
                                subtitle = "Teal ${(preset.tealShadows * 100).toInt()}% · Warm ${(preset.warmHighlights * 100).toInt()}% · ${preset.particleStyle.label}",
                                isSelected = recipe.name == preset.name,
                                onClick = { onApplyRecipe(preset) }
                            )
                        }

                        if (localPresets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "My Saved Presets",
                                style = MaterialTheme.typography.titleMedium,
                                color = LuminousTeal
                            )
                            localPresets.forEach { saved ->
                                RecipeSummaryCard(
                                    title = saved.name,
                                    subtitle = saved.description.ifBlank { "Saved Teal & Warm preset" },
                                    isSelected = recipe.name == saved.name,
                                    onClick = { onApplyRecipe(saved.toRecipe()) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    isSigned: Boolean = false,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = WarmIvory)
            val display = if (isSigned) {
                val pct = (value * 100).toInt()
                if (pct >= 0) "+$pct%" else "$pct%"
            } else {
                "${(value * 100).toInt()}%"
            }
            Text(text = display, style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = HighTealSurface
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun RecipeSummaryCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) HighTealSurface else ElevatedTealSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) ChampagneGold else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = WarmIvory)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SavePresetDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepStudioTeal,
        title = {
            Text("Save Preset", style = MaterialTheme.typography.headlineMedium, color = WarmIvory)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 180) description = it },
                    label = { Text("Lighting / Wedding Notes (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneGold,
                    contentColor = ObsidianTeal
                ),
                modifier = Modifier.testTag("confirm_save_preset_button")
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
