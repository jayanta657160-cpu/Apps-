package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.imaging.FocusPeakingAnalyzer
import com.example.imaging.FocusPeakingColor
import com.example.imaging.FocusPeakingSensitivity
import com.example.imaging.MediaExportHelper
import com.example.imaging.ParticleArtEngine
import com.example.imaging.ParticleArtOverlay
import com.example.imaging.TealWarmColorEngine
import com.example.imaging.TealWarmColorMatrixFilter
import com.example.imaging.TouchLightMote
import com.example.model.CuratedRecipes
import com.example.model.ParticleStyle
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.ElevatedTealSurface
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory
import com.example.viewmodel.AureliaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val TAG = "CameraScreen"
private const val MAX_BURST_SHOTS = 20
private const val BURST_HOLD_THRESHOLD_MS = 280L

private enum class CaptureTimerOption(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    THREE_SEC(3, "3s"),
    TEN_SEC(10, "10s")
}

@Composable
fun CameraScreen(
    viewModel: AureliaViewModel,
    onOpenStudio: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val activeRecipe by viewModel.activeRecipe.collectAsStateWithLifecycle()
    val cameraLutPair by viewModel.cameraLut.collectAsStateWithLifecycle()
    val lightingProfile by viewModel.cameraLighting.collectAsStateWithLifecycle()
    val lastCapturedBitmap by viewModel.lastCapturedBitmap.collectAsStateWithLifecycle()
    val lastCapturedUri by viewModel.lastCapturedUri.collectAsStateWithLifecycle()
    val isAppShareBusy by viewModel.isAppShareBusy.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAutoRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !hasAutoRequestedCameraPermission) {
            hasAutoRequestedCameraPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.notifyUser("Storage permission is needed on Android 9 and below to save photos to Gallery.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadPickedPhoto(context, uri)
            onOpenStudio()
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasMultipleCameras by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isComparingOriginal by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var shutterFlashVisible by remember { mutableStateOf(false) }
    var savedBannerVisible by remember { mutableStateOf(false) }
    var gradedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCameraBound by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showParticleSelector by remember { mutableStateOf(false) }
    var focusPeakingEnabled by remember { mutableStateOf(true) }
    var peakingColor by remember { mutableStateOf(FocusPeakingColor.CRIMSON) }
    var peakingSensitivity by remember { mutableStateOf(FocusPeakingSensitivity.MEDIUM) }
    var focusPeakingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var focusReticleOffset by remember { mutableStateOf<Offset?>(null) }
    var focusReticleTriggerMs by remember { mutableLongStateOf(0L) }
    var timerOption by remember { mutableStateOf(CaptureTimerOption.OFF) }
    var countdownSecondsRemaining by remember { mutableIntStateOf(0) }
    var activeTimerJob by remember { mutableStateOf<Job?>(null) }
    var isShutterPressed by remember { mutableStateOf(false) }
    var isBursting by remember { mutableStateOf(false) }
    var burstShotCount by remember { mutableIntStateOf(0) }
    var activeBurstJob by remember { mutableStateOf<Job?>(null) }
    var showShareAppDialog by remember { mutableStateOf(false) }

    val touchMotes = remember { mutableStateListOf<TouchLightMote>() }
    var latestParticleTime by remember { mutableStateOf(1.5f) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val burstSaveSemaphore = remember { Semaphore(2) }
    val focusPeakingAnalyzer = remember { FocusPeakingAnalyzer() }
    val cameraRefs = remember {
        object {
            var provider: ProcessCameraProvider? = null
            var analysis: ImageAnalysis? = null
            @Volatile var disposed: Boolean = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            isShutterPressed = false
            activeTimerJob?.cancel()
            activeTimerJob = null
            activeBurstJob?.cancel()
            activeBurstJob = null
            cameraRefs.disposed = true
            cameraRefs.analysis?.clearAnalyzer()
            cameraRefs.analysis = null
            cameraRefs.provider?.unbindAll()
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            keepScreenOn = true
        }
    }
    LaunchedEffect(previewView, activeRecipe, lightingProfile, isComparingOriginal) {
        TealWarmColorMatrixFilter.applyToPreviewView(
            previewView = previewView,
            recipe = activeRecipe,
            lighting = lightingProfile,
            enabled = !isComparingOriginal
        )
    }
    val imageCapture = remember(lensFacing) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
            .build()
    }
    LaunchedEffect(imageCapture, flashEnabled) {
        imageCapture.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    var lastAnalyzeMs by remember { mutableLongStateOf(0L) }
    var latestRecipeRef by remember { mutableStateOf(cameraLutPair.first) }
    LaunchedEffect(cameraLutPair) {
        latestRecipeRef = cameraLutPair.first
    }
    var latestPeakingEnabled by remember { mutableStateOf(focusPeakingEnabled) }
    var latestPeakingColor by remember { mutableStateOf(peakingColor) }
    var latestPeakingSensitivity by remember { mutableStateOf(peakingSensitivity) }
    var latestComparingOriginal by remember { mutableStateOf(isComparingOriginal) }
    LaunchedEffect(focusPeakingEnabled, peakingColor, peakingSensitivity, isComparingOriginal) {
        latestPeakingEnabled = focusPeakingEnabled
        latestPeakingColor = peakingColor
        latestPeakingSensitivity = peakingSensitivity
        latestComparingOriginal = isComparingOriginal
        if (!focusPeakingEnabled) {
            focusPeakingBitmap = null
        }
    }

    DisposableEffect(hasCameraPermission, lensFacing, imageCapture, lifecycleOwner) {
        isCameraBound = false
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            var bindingCancelled = false
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                if (bindingCancelled || cameraRefs.disposed) return@addListener
                try {
                    val cameraProvider = providerFuture.get()
                    cameraRefs.provider = cameraProvider
                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    hasMultipleCameras = hasBack && hasFront

                    val resolvedFacing = when {
                        lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> CameraSelector.LENS_FACING_BACK
                        lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.LENS_FACING_FRONT
                        hasBack -> CameraSelector.LENS_FACING_BACK
                        hasFront -> CameraSelector.LENS_FACING_FRONT
                        else -> {
                            cameraError = "No camera lens detected on this device."
                            return@addListener
                        }
                    }
                    if (resolvedFacing != lensFacing) {
                        lensFacing = resolvedFacing
                        return@addListener
                    }

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(resolvedFacing)
                        .build()
                    cameraError = null

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    // Use native YUV_420_888 to avoid CameraX's internal RGBA ImageWriter/ImageReader
                    // maxImages=4 pipeline and read plane 0 luminance directly for Sobel focus peaking.
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    cameraRefs.analysis?.clearAnalyzer()
                    cameraRefs.analysis = analysis

                    var reusableSource: Bitmap? = null

                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                        try {
                            if (bindingCancelled || cameraRefs.disposed) return@setAnalyzer

                            val now = System.currentTimeMillis()
                            val needsLightingSample = latestRecipeRef.adaptiveLighting && now - lastAnalyzeMs > 550L
                            val needsLegacyBitmap = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                            val peakingActive = latestPeakingEnabled && !latestComparingOriginal
                            val mirrorFront = lensFacing == CameraSelector.LENS_FACING_FRONT

                            if (peakingActive) {
                                val edgeBitmap = focusPeakingAnalyzer.detectSobelEdges(
                                    proxy = proxy,
                                    color = latestPeakingColor,
                                    sensitivity = latestPeakingSensitivity,
                                    mirrorHorizontal = mirrorFront
                                )
                                if (edgeBitmap != null && !bindingCancelled && !cameraRefs.disposed) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        if (latestPeakingEnabled && !cameraRefs.disposed) {
                                            focusPeakingBitmap = edgeBitmap
                                        }
                                    }
                                }
                            }

                            if (needsLightingSample || needsLegacyBitmap) {
                                val targetEdge = if (needsLegacyBitmap) 640 else 180
                                val rawBitmap = analysisProxyToBitmap(proxy, reusableSource, targetEdge)
                                if (rawBitmap != null) {
                                    reusableSource = rawBitmap
                                    val oriented = MediaExportHelper.applyRotationAndFlip(
                                        rawBitmap,
                                        proxy.imageInfo.rotationDegrees.toFloat(),
                                        mirrorHorizontal = mirrorFront
                                    )

                                    if (needsLightingSample) {
                                        lastAnalyzeMs = now
                                        val profile = TealWarmColorEngine.analyzeLighting(oriented)
                                        viewModel.updateCameraLightingProfile(profile)
                                    }

                                    if (needsLegacyBitmap && !bindingCancelled && !cameraRefs.disposed) {
                                        val displayCopy = oriented.copy(Bitmap.Config.ARGB_8888, false)
                                        ContextCompat.getMainExecutor(context).execute {
                                            if (!cameraRefs.disposed) {
                                                gradedPreviewBitmap = displayCopy
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Frame analysis skipped: ${e.message}")
                        } finally {
                            proxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        analysis,
                        imageCapture
                    )
                    boundCamera = camera
                    isCameraBound = true
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX binding failed", e)
                    boundCamera = null
                    isCameraBound = false
                    cameraError = e.localizedMessage ?: "Camera unavailable"
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                bindingCancelled = true
                boundCamera = null
                cameraRefs.analysis?.clearAnalyzer()
                cameraRefs.analysis = null
                cameraRefs.provider?.unbindAll()
            }
        }
    }

    DisposableEffect(boundCamera, lifecycleOwner) {
        val cam = boundCamera
        if (cam == null) {
            onDispose { }
        } else {
            val zoomLiveData = cam.cameraInfo.zoomState
            val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
                zoomRatio = state.zoomRatio
                minZoomRatio = state.minZoomRatio
                maxZoomRatio = state.maxZoomRatio
            }
            zoomLiveData.observe(lifecycleOwner, observer)
            onDispose {
                zoomLiveData.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(focusReticleTriggerMs) {
        if (focusReticleTriggerMs == 0L) return@LaunchedEffect
        val stamp = focusReticleTriggerMs
        delay(1200L)
        if (focusReticleTriggerMs == stamp) {
            focusReticleOffset = null
        }
    }

    fun triggerTapToFocus(offset: Offset) {
        focusReticleOffset = offset
        focusReticleTriggerMs = System.currentTimeMillis()
        val cam = boundCamera ?: return
        runCatching {
            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        }
    }

    fun executePhotoCapture() {
        if (isCapturing) return
        isCapturing = true
        scope.launch {
            shutterFlashVisible = true
            delay(120)
            shutterFlashVisible = false
        }
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val decodedResult = try {
                        val decoded = imageProxyToCapturedBitmap(image)
                        MediaExportHelper.applyRotationAndFlip(
                            decoded,
                            image.imageInfo.rotationDegrees.toFloat(),
                            mirrorHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode captured image", e)
                        null
                    } finally {
                        image.close()
                    }
                    if (decodedResult == null) {
                        ContextCompat.getMainExecutor(context).execute {
                            isCapturing = false
                            viewModel.notifyUser("Capture decode failed")
                        }
                        return
                    }
                    scope.launch {
                        try {
                            val recipe = activeRecipe
                            val graded = withContext(Dispatchers.Default) {
                                val profile = TealWarmColorEngine.analyzeLighting(decodedResult)
                                val lut = TealWarmColorEngine.buildLut33(recipe, profile)
                                val colorGraded = TealWarmColorEngine.applyLut(decodedResult, lut)
                                if (recipe.particleStyle != ParticleStyle.NONE && recipe.bakeParticlesOnExport) {
                                    ParticleArtEngine.bakeParticlesOntoBitmap(
                                        colorGraded,
                                        recipe,
                                        touchMotes.toList(),
                                        latestParticleTime
                                    )
                                } else {
                                    colorGraded
                                }
                            }
                            val saveRes = MediaExportHelper.saveBitmapToGallery(
                                context,
                                graded,
                                "JDCam"
                            )
                            viewModel.recordCameraCapture(
                                rawBitmap = decodedResult,
                                gradedBitmap = graded,
                                savedUri = saveRes.getOrNull(),
                                recipe = recipe
                            )
                            saveRes.onSuccess {
                                viewModel.notifyUser("Captured & saved to Gallery (Pictures/JDCam)")
                                savedBannerVisible = true
                                delay(2200)
                                savedBannerVisible = false
                            }.onFailure { err ->
                                viewModel.notifyUser("Capture save failed: ${err.message}")
                            }
                        } catch (e: Exception) {
                            viewModel.notifyUser("Capture failed: ${e.message}")
                        } finally {
                            isCapturing = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    viewModel.notifyUser("Shutter error: ${exception.message}")
                }
            }
        )
    }

    fun cancelSelfTimer() {
        activeTimerJob?.cancel()
        activeTimerJob = null
        countdownSecondsRemaining = 0
    }

    fun startSelfTimer(waitSec: Int) {
        activeTimerJob?.cancel()
        activeTimerJob = scope.launch {
            try {
                for (sec in waitSec downTo 1) {
                    countdownSecondsRemaining = sec
                    delay(1000L)
                }
                countdownSecondsRemaining = 0
                executePhotoCapture()
            } finally {
                countdownSecondsRemaining = 0
                activeTimerJob = null
            }
        }
    }

    fun startBurstCapture() {
        if (isCapturing || isBursting) return
        activeBurstJob?.cancel()
        activeBurstJob = scope.launch {
            isBursting = true
            burstShotCount = 0
            var savedCount = 0
            val saveJobs = mutableListOf<Job>()
            try {
                while (isActive && isShutterPressed && burstShotCount < MAX_BURST_SHOTS) {
                    val rawBitmap = captureOrientedBitmap(
                        imageCapture = imageCapture,
                        executor = captureExecutor,
                        mirrorHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ).getOrElse { err ->
                        Log.w(TAG, "Burst frame capture stopped: ${err.message}")
                        break
                    }
                    val shotIndex = burstShotCount + 1
                    burstShotCount = shotIndex

                    launch {
                        shutterFlashVisible = true
                        delay(55L)
                        shutterFlashVisible = false
                    }

                    val recipeSnapshot = activeRecipe
                    val motesSnapshot = touchMotes.toList()
                    val particleTimeSnapshot = latestParticleTime
                    saveJobs += launch {
                        burstSaveSemaphore.withPermit {
                            val graded = withContext(Dispatchers.Default) {
                                val profile = TealWarmColorEngine.analyzeLighting(rawBitmap)
                                val lut = TealWarmColorEngine.buildLut33(recipeSnapshot, profile)
                                val colorGraded = TealWarmColorEngine.applyLut(rawBitmap, lut)
                                if (recipeSnapshot.particleStyle != ParticleStyle.NONE && recipeSnapshot.bakeParticlesOnExport) {
                                    ParticleArtEngine.bakeParticlesOntoBitmap(
                                        colorGraded,
                                        recipeSnapshot,
                                        motesSnapshot,
                                        particleTimeSnapshot
                                    )
                                } else {
                                    colorGraded
                                }
                            }
                            val prefix = "JDCam_Burst_${shotIndex.toString().padStart(2, '0')}"
                            MediaExportHelper.saveBitmapToGallery(context, graded, prefix)
                                .onSuccess { uri ->
                                    savedCount++
                                    viewModel.recordCameraCapture(
                                        rawBitmap = rawBitmap,
                                        gradedBitmap = graded,
                                        savedUri = uri,
                                        recipe = recipeSnapshot
                                    )
                                }
                        }
                    }

                    if (isShutterPressed && shotIndex < MAX_BURST_SHOTS) {
                        delay(50L)
                    }
                }
            } finally {
                isBursting = false
                if (saveJobs.any { it.isActive }) {
                    isCapturing = true
                }
                saveJobs.joinAll()
                isCapturing = false
                burstShotCount = 0
                activeBurstJob = null
                if (savedCount > 0) {
                    viewModel.notifyUser(
                        "Burst saved: $savedCount photo${if (savedCount == 1) "" else "s"} in Pictures/JDCam"
                    )
                    savedBannerVisible = true
                    delay(2400L)
                    savedBannerVisible = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianTeal)
    ) {
        if (hasCameraPermission && cameraError == null) {
            // Real-time Phone Camera Viewfinder with two-finger pinch-to-zoom & tap-to-focus
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(boundCamera, previewView) {
                        detectTapGestures { offset ->
                            triggerTapToFocus(offset)
                        }
                    }
                    .pointerInput(boundCamera, minZoomRatio, maxZoomRatio) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    val zoom = event.calculateZoom()
                                    val cam = boundCamera
                                    if (cam != null && zoom != 1f && maxZoomRatio > minZoomRatio) {
                                        val nextZoom = (zoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                                        zoomRatio = nextZoom
                                        cam.cameraControl.setZoomRatio(nextZoom)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                // CameraX PreviewView with GPU ColorMatrixFilter on API 31+ (cleared during Hold-to-Compare)
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // API < 31 fallback: apply Compose ColorFilter.colorMatrix directly to the live frame
                val legacyFrame = gradedPreviewBitmap
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && legacyFrame != null && !isComparingOriginal) {
                    val matrixFilter = remember(activeRecipe, lightingProfile) {
                        TealWarmColorMatrixFilter.toComposeColorFilter(activeRecipe, lightingProfile)
                    }
                    Image(
                        bitmap = legacyFrame.asImageBitmap(),
                        contentDescription = "Live Teal & Warm graded viewfinder",
                        contentScale = ContentScale.Crop,
                        colorFilter = matrixFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Real-time Sobel Focus Peaking Viewfinder Overlay (hidden on Hold-to-Compare & never baked into captures)
                val peakingFrame = focusPeakingBitmap
                if (focusPeakingEnabled && peakingFrame != null && !isComparingOriginal) {
                    Image(
                        bitmap = peakingFrame.asImageBitmap(),
                        contentDescription = "Focus peaking sharp edge highlights",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_focus_peaking_overlay")
                    )
                }

                // Live Particle Art Layer
                if (!isComparingOriginal) {
                    ParticleArtOverlay(
                        recipe = activeRecipe,
                        touchMotes = touchMotes,
                        allowTouchPaint = true,
                        onTimeTick = { latestParticleTime = it },
                        onViewfinderTap = { offset -> triggerTapToFocus(offset) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Tap-to-Focus Reticle Indicator
                val reticle = focusReticleOffset
                if (reticle != null) {
                    val reticleHalfPx = with(LocalDensity.current) { 32.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (reticle.x - reticleHalfPx).roundToInt(),
                                    (reticle.y - reticleHalfPx).roundToInt()
                                )
                            }
                            .size(64.dp)
                            .border(2.dp, ChampagneGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ChampagneGold)
                        )
                    }
                }
            }

            // Self-timer visual countdown overlay
            if (countdownSecondsRemaining > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("camera_timer_countdown_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = ObsidianTeal.copy(alpha = 0.72f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(132.dp)
                                .border(3.dp, ChampagneGold, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = countdownSecondsRemaining.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = ChampagneGold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = DeepStudioTeal.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "SELF-TIMER · TAP SHUTTER TO CANCEL",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmIvory,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Shutter flash feedback
            AnimatedVisibility(
                visible = shutterFlashVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WarmIvory.copy(alpha = 0.65f))
                )
            }
        } else {
            // Graceful permission or camera-unavailable studio fallback
            CameraUnavailablePanel(
                hasPermission = hasCameraPermission,
                errorMessage = cameraError,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImportPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenStudio = onOpenStudio
            )
        }

        // Top HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianTeal.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active look + Live phone camera badge
                Surface(
                    color = DeepStudioTeal.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        1.dp,
                        ChampagneGold.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isCameraBound) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(LuminousTeal)
                                )
                            }
                            Text(
                                text = activeRecipe.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = WarmIvory
                            )
                        }
                        val zoomBadge = String.format(Locale.US, "%.1fx", zoomRatio)
                        val sceneText = if (activeRecipe.adaptiveLighting) {
                            "LIVE $zoomBadge · ${lightingProfile.detectedScene.uppercase()}"
                        } else {
                            "LIVE $zoomBadge · MANUAL LUT"
                        }
                        Text(
                            text = sceneText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneGold
                        )
                    }
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
                            .background(DeepStudioTeal.copy(alpha = 0.82f))
                            .testTag("camera_pick_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(id = R.string.import_from_gallery),
                            tint = WarmIvory
                        )
                    }

                    // Hold to compare original
                    Surface(
                        color = if (isComparingOriginal) ChampagneGold else DeepStudioTeal.copy(alpha = 0.82f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isComparingOriginal = true
                                        tryAwaitRelease()
                                        isComparingOriginal = false
                                    }
                                )
                            }
                            .testTag("camera_compare_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Compare,
                                contentDescription = stringResource(id = R.string.compare_hold),
                                tint = if (isComparingOriginal) ObsidianTeal else WarmIvory
                            )
                        }
                    }

                    IconButton(
                        onClick = { flashEnabled = !flashEnabled },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DeepStudioTeal.copy(alpha = 0.82f))
                            .testTag("camera_flash_button")
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (flashEnabled) ChampagneGold else WarmIvory
                        )
                    }

                    IconButton(
                        enabled = hasMultipleCameras,
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DeepStudioTeal.copy(alpha = if (hasMultipleCameras) 0.82f else 0.4f))
                            .testTag("camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FlipCameraAndroid,
                            contentDescription = "Switch Camera Lens",
                            tint = if (hasMultipleCameras) WarmIvory else WarmIvory.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Self-Timer & Focus Peaking HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val toggleDesc = stringResource(id = R.string.focus_peaking_toggle_desc)
                val colorDesc = stringResource(id = R.string.focus_peaking_color_desc)
                val sensitivityDesc = stringResource(id = R.string.focus_peaking_sensitivity_desc)
                val timer3sDesc = stringResource(id = R.string.timer_3s_desc)
                val timer10sDesc = stringResource(id = R.string.timer_10s_desc)

                FilterChip(
                    selected = timerOption == CaptureTimerOption.THREE_SEC,
                    onClick = {
                        if (countdownSecondsRemaining > 0) cancelSelfTimer()
                        timerOption = if (timerOption == CaptureTimerOption.THREE_SEC) {
                            CaptureTimerOption.OFF
                        } else {
                            CaptureTimerOption.THREE_SEC
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "3s",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ChampagneGold,
                        selectedLabelColor = ObsidianTeal,
                        selectedLeadingIconColor = ObsidianTeal,
                        containerColor = DeepStudioTeal.copy(alpha = 0.82f),
                        labelColor = WarmIvory,
                        iconColor = WarmIvory
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = timerOption == CaptureTimerOption.THREE_SEC,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = ChampagneGold
                    ),
                    modifier = Modifier
                        .semantics { contentDescription = timer3sDesc }
                        .testTag("camera_timer_3s_button")
                )

                FilterChip(
                    selected = timerOption == CaptureTimerOption.TEN_SEC,
                    onClick = {
                        if (countdownSecondsRemaining > 0) cancelSelfTimer()
                        timerOption = if (timerOption == CaptureTimerOption.TEN_SEC) {
                            CaptureTimerOption.OFF
                        } else {
                            CaptureTimerOption.TEN_SEC
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "10s",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ChampagneGold,
                        selectedLabelColor = ObsidianTeal,
                        selectedLeadingIconColor = ObsidianTeal,
                        containerColor = DeepStudioTeal.copy(alpha = 0.82f),
                        labelColor = WarmIvory,
                        iconColor = WarmIvory
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = timerOption == CaptureTimerOption.TEN_SEC,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = ChampagneGold
                    ),
                    modifier = Modifier
                        .semantics { contentDescription = timer10sDesc }
                        .testTag("camera_timer_10s_button")
                )

                FilterChip(
                    selected = focusPeakingEnabled,
                    onClick = { focusPeakingEnabled = !focusPeakingEnabled },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = toggleDesc,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (focusPeakingEnabled) "PEAKING ON" else "PEAKING OFF",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(peakingColor.rgb or (0xFF shl 24)).copy(alpha = 0.24f),
                        selectedLabelColor = WarmIvory,
                        selectedLeadingIconColor = Color(peakingColor.rgb or (0xFF shl 24)),
                        containerColor = DeepStudioTeal.copy(alpha = 0.82f),
                        labelColor = WarmIvory.copy(alpha = 0.8f),
                        iconColor = WarmIvory.copy(alpha = 0.8f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = focusPeakingEnabled,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = Color(peakingColor.rgb or (0xFF shl 24)).copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.testTag("camera_focus_peaking_button")
                )

                if (focusPeakingEnabled) {
                    FilterChip(
                        selected = true,
                        onClick = { peakingColor = peakingColor.next() },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(peakingColor.rgb or (0xFF shl 24)))
                            )
                        },
                        label = {
                            Text(
                                text = peakingColor.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepStudioTeal.copy(alpha = 0.86f),
                            selectedLabelColor = WarmIvory
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = Color(peakingColor.rgb or (0xFF shl 24)).copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .semantics {
                                contentDescription = colorDesc
                            }
                            .testTag("camera_peaking_color_button")
                    )

                    FilterChip(
                        selected = true,
                        onClick = { peakingSensitivity = peakingSensitivity.next() },
                        label = {
                            Text(
                                text = "Edge: ${peakingSensitivity.label}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepStudioTeal.copy(alpha = 0.86f),
                            selectedLabelColor = ChampagneGold
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = ChampagneGold.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .semantics {
                                contentDescription = sensitivityDesc
                            }
                            .testTag("camera_peaking_sensitivity_button")
                    )
                }

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
                        containerColor = DeepStudioTeal.copy(alpha = 0.86f),
                        labelColor = ChampagneGold,
                        iconColor = ChampagneGold
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = ChampagneGold.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.testTag("camera_share_app_button")
                )
            }

            if (isBursting) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = DeepStudioTeal.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .border(1.dp, LuminousTeal, RoundedCornerShape(12.dp))
                        .testTag("camera_burst_counter_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LuminousTeal)
                        )
                        Text(
                            text = "BURST CAPTURE · ${burstShotCount.toString().padStart(2, '0')} / $MAX_BURST_SHOTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarmIvory
                        )
                    }
                }
            } else if (isComparingOriginal) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ChampagneGold,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "ORIGINAL UNGRADED FEED",
                        style = MaterialTheme.typography.labelMedium,
                        color = ObsidianTeal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            } else if (savedBannerVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = LuminousTeal,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .then(
                            if (lastCapturedUri != null) {
                                Modifier.clickable {
                                    lastCapturedUri?.let { uri ->
                                        viewModel.shareCapturedPhotoUri(context, uri)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("camera_saved_share_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lastCapturedUri != null) {
                                "SAVED TO GALLERY · TAP TO SHARE"
                            } else {
                                "SAVED TO GALLERY · PICTURES/JDCAM"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = ObsidianTeal
                        )
                        if (lastCapturedUri != null) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(id = R.string.share_photo),
                                tint = ObsidianTeal,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Camera Controls Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ObsidianTeal.copy(alpha = 0.94f))
                    )
                )
                .padding(top = 20.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional Particle Art quick bar
            AnimatedVisibility(visible = showParticleSelector) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParticleStyle.entries.forEach { style ->
                        val selected = activeRecipe.particleStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.updateRecipe { it.copy(particleStyle = style) }
                            },
                            label = { Text(style.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LuminousTeal,
                                selectedLabelColor = ObsidianTeal,
                                containerColor = ElevatedTealSurface,
                                labelColor = WarmIvory
                            ),
                            modifier = Modifier.testTag("camera_particle_${style.name.lowercase()}")
                        )
                    }
                }
            }

            // Preset Look Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CuratedRecipes.all.forEach { preset ->
                    val selected = activeRecipe.name == preset.name
                    Surface(
                        color = if (selected) ChampagneGold else ElevatedTealSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable { viewModel.applyRecipe(preset, announce = false) }
                            .border(
                                1.dp,
                                if (selected) ChampagneGold else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("camera_preset_${preset.name.lowercase().replace(" ", "_")}")
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ObsidianTeal else WarmIvory,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Action Row: Gallery / Shutter / Particle & Studio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Photo Picker OR Latest Capture thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedTealSurface)
                        .border(1.dp, ChampagneGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            if (lastCapturedBitmap != null) {
                                onOpenStudio()
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                        .testTag("camera_gallery_button"),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = lastCapturedBitmap
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Open latest photo in Studio",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(id = R.string.import_from_gallery),
                            tint = WarmIvory
                        )
                    }
                }

                // Shutter Button:
                // - Quick tap: instant capture (or 3s / 10s self-timer countdown with tap-to-cancel)
                // - Hold down (>= 280ms when timer is Off): continuous rapid Burst Capture until released
                val captureDesc = stringResource(id = R.string.capture_photo)
                val cancelTimerDesc = stringResource(id = R.string.timer_cancel_desc)
                val burstCapturingDesc = stringResource(id = R.string.burst_capturing_desc)
                val isTimerRunning = countdownSecondsRemaining > 0
                val shutterScale by animateFloatAsState(
                    targetValue = when {
                        isBursting -> 1.08f
                        isShutterPressed -> 0.93f
                        else -> 1f
                    },
                    label = "shutter_scale"
                )
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(
                            width = 3.dp,
                            color = when {
                                isBursting -> LuminousTeal
                                isTimerRunning -> LuminousTeal
                                else -> ChampagneGold
                            },
                            shape = CircleShape
                        )
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isBursting -> LuminousTeal
                                isCapturing -> LuminousTeal
                                isTimerRunning -> ChampagneGold
                                else -> WarmIvory
                            }
                        )
                        .pointerInput(imageCapture) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!hasCameraPermission || !isCameraBound || cameraError != null) {
                                    return@awaitEachGesture
                                }
                                down.consume()

                                if (countdownSecondsRemaining > 0) {
                                    cancelSelfTimer()
                                    do {
                                        val ev = awaitPointerEvent()
                                    } while (ev.changes.any { it.id == down.id && it.pressed })
                                    return@awaitEachGesture
                                }
                                if (isCapturing || isBursting) return@awaitEachGesture

                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    return@awaitEachGesture
                                }

                                isShutterPressed = true
                                var burstTriggered = false
                                val holdWatcherJob = if (timerOption == CaptureTimerOption.OFF) {
                                    scope.launch {
                                        delay(BURST_HOLD_THRESHOLD_MS)
                                        if (isShutterPressed && !isCapturing && !isBursting) {
                                            burstTriggered = true
                                            startBurstCapture()
                                        }
                                    }
                                } else {
                                    null
                                }

                                try {
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.id == down.id && it.pressed })
                                } finally {
                                    isShutterPressed = false
                                    holdWatcherJob?.cancel()
                                }

                                if (!burstTriggered) {
                                    val waitSec = timerOption.seconds
                                    if (waitSec <= 0) {
                                        executePhotoCapture()
                                    } else {
                                        startSelfTimer(waitSec)
                                    }
                                }
                            }
                        }
                        .semantics {
                            contentDescription = when {
                                isBursting -> burstCapturingDesc
                                isTimerRunning -> cancelTimerDesc
                                else -> captureDesc
                            }
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isBursting -> {
                            Text(
                                text = burstShotCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = ObsidianTeal
                            )
                        }
                        isCapturing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = ObsidianTeal,
                                strokeWidth = 3.dp
                            )
                        }
                        isTimerRunning -> {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = cancelTimerDesc,
                                tint = ObsidianTeal,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        timerOption != CaptureTimerOption.OFF -> {
                            Text(
                                text = timerOption.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = ObsidianTeal
                            )
                        }
                    }
                }

                // Particle Art & Fine-Tune Controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showParticleSelector = !showParticleSelector },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeRecipe.particleStyle != ParticleStyle.NONE) {
                                    LuminousTeal.copy(alpha = 0.25f)
                                } else {
                                    ElevatedTealSurface
                                }
                            )
                            .border(
                                1.dp,
                                if (showParticleSelector) LuminousTeal else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                            .testTag("camera_particles_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Particle Art Effects",
                            tint = if (activeRecipe.particleStyle != ParticleStyle.NONE) LuminousTeal else WarmIvory
                        )
                    }

                    IconButton(
                        onClick = onOpenStudio,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElevatedTealSurface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .testTag("camera_open_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Open Grading Studio",
                            tint = ChampagneGold
                        )
                    }
                }
            }

            if (timerOption == CaptureTimerOption.OFF && countdownSecondsRemaining == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.burst_hint).uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBursting) LuminousTeal else WarmIvory.copy(alpha = 0.68f),
                    modifier = Modifier.testTag("camera_burst_hint_label")
                )
            }
        }
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
private fun CameraUnavailablePanel(
    hasPermission: Boolean,
    errorMessage: String?,
    onRequestPermission: () -> Unit,
    onImportPhoto: () -> Unit,
    onOpenStudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ChampagneGold.copy(alpha = 0.14f))
                .border(1.dp, ChampagneGold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (!hasPermission) {
                stringResource(id = R.string.camera_permission_title)
            } else {
                "Camera Feed Unavailable"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = WarmIvory,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage ?: stringResource(id = R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneGold,
                    contentColor = ObsidianTeal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("grant_camera_permission_button")
            ) {
                Text(stringResource(id = R.string.grant_camera_permission))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onImportPhoto,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("fallback_import_photo_button")
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(id = R.string.import_from_gallery))
            }
            Button(
                onClick = onOpenStudio,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LuminousTeal,
                    contentColor = ObsidianTeal
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("fallback_open_studio_button")
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open Studio")
            }
        }
    }
}

private fun analysisProxyToBitmap(
    proxy: ImageProxy,
    reusable: Bitmap?,
    maxLongestEdge: Int
): Bitmap? {
    if (proxy.format == ImageFormat.YUV_420_888 && proxy.planes.size >= 3) {
        return yuv420ProxyToBitmap(proxy, reusable, maxLongestEdge)
    }
    if (proxy.planes.isEmpty()) return null
    return rgbaProxyToBitmap(proxy, reusable)
}

private fun yuv420ProxyToBitmap(
    proxy: ImageProxy,
    reusable: Bitmap?,
    maxLongestEdge: Int
): Bitmap? {
    val srcW = proxy.width
    val srcH = proxy.height
    if (srcW <= 0 || srcH <= 0) return null

    val step = maxOf(1, maxOf(srcW, srcH) / maxLongestEdge.coerceAtLeast(32))
    val outW = srcW / step
    val outH = srcH / step
    if (outW <= 0 || outH <= 0) return null

    val yPlane = proxy.planes[0]
    val uPlane = proxy.planes[1]
    val vPlane = proxy.planes[2]

    val yBuf = yPlane.buffer.apply { rewind() }
    val uBuf = uPlane.buffer.apply { rewind() }
    val vBuf = vPlane.buffer.apply { rewind() }

    val yRowStride = yPlane.rowStride
    val yPixStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixStride = vPlane.pixelStride

    val yLimit = yBuf.limit()
    val uLimit = uBuf.limit()
    val vLimit = vBuf.limit()

    val bmp = if (reusable != null && reusable.width == outW && reusable.height == outH && reusable.isMutable) {
        reusable
    } else {
        Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    }

    val pixels = IntArray(outW * outH)
    for (oy in 0 until outH) {
        val sy = oy * step
        val uvY = sy shr 1
        val yRowOffset = sy * yRowStride
        val uRowOffset = uvY * uRowStride
        val vRowOffset = uvY * vRowStride
        val outRow = oy * outW

        for (ox in 0 until outW) {
            val sx = ox * step
            val yIdx = yRowOffset + sx * yPixStride
            val uvX = sx shr 1
            val uIdx = uRowOffset + uvX * uPixStride
            val vIdx = vRowOffset + uvX * vPixStride

            val y = if (yIdx < yLimit) yBuf.get(yIdx).toInt() and 0xFF else 0
            val u = if (uIdx < uLimit) (uBuf.get(uIdx).toInt() and 0xFF) - 128 else 0
            val v = if (vIdx < vLimit) (vBuf.get(vIdx).toInt() and 0xFF) - 128 else 0

            val r = (y + ((1436 * v) shr 10)).coerceIn(0, 255)
            val g = (y - ((352 * u + 731 * v) shr 10)).coerceIn(0, 255)
            val b = (y + ((1815 * u) shr 10)).coerceIn(0, 255)
            pixels[outRow + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    bmp.setPixels(pixels, 0, outW, 0, 0, outW, outH)
    return bmp
}

private fun rgbaProxyToBitmap(proxy: ImageProxy, reusable: Bitmap?): Bitmap {
    val plane = proxy.planes[0]
    val width = proxy.width
    val height = proxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer
    buffer.rewind()

    val bmp = if (reusable != null && reusable.width == width && reusable.height == height && reusable.isMutable) {
        reusable
    } else {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    if (rowStride == width * pixelStride && pixelStride == 4) {
        bmp.copyPixelsFromBuffer(buffer)
    } else {
        val rowBytes = ByteArray(rowStride)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val remaining = buffer.remaining()
            if (remaining <= 0) break
            val toRead = minOf(rowStride, remaining)
            buffer.get(rowBytes, 0, toRead)
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = x * pixelStride
                if (idx + 3 < toRead) {
                    val r = rowBytes[idx].toInt() and 0xFF
                    val g = rowBytes[idx + 1].toInt() and 0xFF
                    val b = rowBytes[idx + 2].toInt() and 0xFF
                    val a = rowBytes[idx + 3].toInt() and 0xFF
                    pixels[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    }
    return bmp
}

private suspend fun captureOrientedBitmap(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    mirrorHorizontal: Boolean
): Result<Bitmap> = suspendCancellableCoroutine { cont ->
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val decodedResult = try {
                    val decoded = imageProxyToCapturedBitmap(image)
                    MediaExportHelper.applyRotationAndFlip(
                        decoded,
                        image.imageInfo.rotationDegrees.toFloat(),
                        mirrorHorizontal = mirrorHorizontal
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode captured image", e)
                    null
                } finally {
                    image.close()
                }
                if (cont.isActive) {
                    if (decodedResult != null) {
                        cont.resume(Result.success(decodedResult))
                    } else {
                        cont.resume(Result.failure(IllegalStateException("Capture decode failed")))
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) {
                    cont.resume(Result.failure(exception))
                }
            }
        }
    )
}

private fun imageProxyToCapturedBitmap(image: ImageProxy): Bitmap {
    if (image.format == ImageFormat.JPEG && image.planes.isNotEmpty()) {
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (decoded != null) return decoded
    }
    return image.toBitmap()
}
