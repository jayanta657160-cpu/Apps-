package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.CapturedPhotoEntity
import com.example.data.local.CapturedPhotoRepository
import com.example.data.local.LocalPresetEntity
import com.example.data.local.LocalPresetRepository
import com.example.imaging.BackgroundRemovalEngine
import com.example.imaging.CleanupStroke
import com.example.imaging.LightingProfile
import com.example.imaging.MediaExportHelper
import com.example.imaging.ObjectCleanupEngine
import com.example.imaging.ParticleArtEngine
import com.example.imaging.ShareHelper
import com.example.imaging.TealWarmColorEngine
import com.example.imaging.TouchLightMote
import com.example.model.CuratedRecipes
import com.example.model.GradingRecipe
import com.example.model.ParticleStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AureliaViewModel(
    private val localPresetRepository: LocalPresetRepository,
    private val capturedPhotoRepository: CapturedPhotoRepository
) : ViewModel() {

    private val _activeRecipe = MutableStateFlow(CuratedRecipes.signature)
    val activeRecipe: StateFlow<GradingRecipe> = _activeRecipe.asStateFlow()

    private val _cameraLut = MutableStateFlow(
        CuratedRecipes.signature to TealWarmColorEngine.buildLut33(
            CuratedRecipes.signature,
            LightingProfile()
        )
    )
    val cameraLut: StateFlow<Pair<GradingRecipe, IntArray>> = _cameraLut.asStateFlow()

    private val _cameraLighting = MutableStateFlow(LightingProfile())
    val cameraLighting: StateFlow<LightingProfile> = _cameraLighting.asStateFlow()

    private val cleanupHistory = ArrayDeque<Bitmap>()
    private val _canUndoCleanup = MutableStateFlow(false)
    val canUndoCleanup: StateFlow<Boolean> = _canUndoCleanup.asStateFlow()

    private val _originalStudioBitmap = MutableStateFlow<Bitmap?>(null)
    val originalStudioBitmap: StateFlow<Bitmap?> = _originalStudioBitmap.asStateFlow()

    private val _gradedStudioBitmap = MutableStateFlow<Bitmap?>(null)
    val gradedStudioBitmap: StateFlow<Bitmap?> = _gradedStudioBitmap.asStateFlow()

    private val _studioLighting = MutableStateFlow(LightingProfile())
    val studioLighting: StateFlow<LightingProfile> = _studioLighting.asStateFlow()

    private val _studioHistogram = MutableStateFlow(FloatArray(32))
    val studioHistogram: StateFlow<FloatArray> = _studioHistogram.asStateFlow()

    private val _studioSourceLabel = MutableStateFlow("Wedding Garden (Ref)")
    val studioSourceLabel: StateFlow<String> = _studioSourceLabel.asStateFlow()

    private val _isProcessingStudio = MutableStateFlow(false)
    val isProcessingStudio: StateFlow<Boolean> = _isProcessingStudio.asStateFlow()

    private val _lastCapturedBitmap = MutableStateFlow<Bitmap?>(null)
    val lastCapturedBitmap: StateFlow<Bitmap?> = _lastCapturedBitmap.asStateFlow()

    private val _lastCapturedUri = MutableStateFlow<Uri?>(null)
    val lastCapturedUri: StateFlow<Uri?> = _lastCapturedUri.asStateFlow()

    private val _isAppShareBusy = MutableStateFlow(false)
    val isAppShareBusy: StateFlow<Boolean> = _isAppShareBusy.asStateFlow()

    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

    val localPresets: StateFlow<List<LocalPresetEntity>> =
        localPresetRepository.observePresets()
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing local presets", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val capturedPhotos: StateFlow<List<CapturedPhotoEntity>> =
        capturedPhotoRepository.observeCapturedPhotos()
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing captured photos", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            combine(_originalStudioBitmap, _activeRecipe) { bmp, recipe -> bmp to recipe }
                .collectLatest { (source, recipe) ->
                    if (source == null) return@collectLatest
                    _isProcessingStudio.value = true
                    val result = withContext(Dispatchers.Default) {
                        val lighting = TealWarmColorEngine.analyzeLighting(source)
                        val lut = TealWarmColorEngine.buildLut33(recipe, lighting)
                        val graded = TealWarmColorEngine.applyLut(source, lut)
                        val hist = TealWarmColorEngine.computeHistogram(graded)
                        Triple(lighting, graded, hist)
                    }
                    _studioLighting.value = result.first
                    _gradedStudioBitmap.value = result.second
                    _studioHistogram.value = result.third
                    _isProcessingStudio.value = false
                }
        }

        viewModelScope.launch {
            combine(_activeRecipe, _cameraLighting) { recipe, lighting -> recipe to lighting }
                .collectLatest { (recipe, lighting) ->
                    val lut = withContext(Dispatchers.Default) {
                        TealWarmColorEngine.buildLut33(recipe, lighting)
                    }
                    _cameraLut.value = recipe to lut
                }
        }
    }

    fun ensureDefaultSampleLoaded(context: Context) {
        if (_originalStudioBitmap.value != null) return
        loadReferenceSample(context, R.drawable.img_sample_wedding_garden, "Wedding Garden (Ref)")
    }

    fun loadReferenceSample(context: Context, @DrawableRes resId: Int, label: String) {
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val res = MediaExportHelper.loadBitmapFromDrawable(context, resId, maxEdgePx = 1500)
            res.onSuccess { bmp ->
                cleanupHistory.clear()
                _canUndoCleanup.value = false
                _originalStudioBitmap.value = bmp
                _studioSourceLabel.value = label
            }.onFailure { err ->
                _statusMessages.tryEmit(err.message ?: "Could not load sample image.")
                _isProcessingStudio.value = false
            }
        }
    }

    fun loadPickedPhoto(context: Context, uri: Uri, label: String = "Imported Photo") {
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val res = MediaExportHelper.loadBitmapFromUri(context, uri, maxEdgePx = 1800)
            res.onSuccess { bmp ->
                cleanupHistory.clear()
                _canUndoCleanup.value = false
                _originalStudioBitmap.value = bmp
                _studioSourceLabel.value = label
            }.onFailure { err ->
                _statusMessages.tryEmit(err.message ?: "Could not load selected photo.")
                _isProcessingStudio.value = false
            }
        }
    }

    fun recordCameraCapture(
        rawBitmap: Bitmap,
        gradedBitmap: Bitmap,
        savedUri: Uri? = null,
        recipe: GradingRecipe = _activeRecipe.value
    ) {
        cleanupHistory.clear()
        _canUndoCleanup.value = false
        _lastCapturedBitmap.value = gradedBitmap
        _lastCapturedUri.value = savedUri
        _originalStudioBitmap.value = rawBitmap
        _studioSourceLabel.value = "Latest Capture"
        if (savedUri != null) {
            viewModelScope.launch {
                try {
                    capturedPhotoRepository.recordCapture(
                        uriString = savedUri.toString(),
                        recipeName = recipe.name,
                        particleStyle = recipe.particleStyle.name
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to record capture URI", e)
                }
            }
        }
    }

    fun deleteCapturedPhoto(id: Long) {
        viewModelScope.launch {
            try {
                capturedPhotoRepository.deleteCapture(id)
                _statusMessages.tryEmit("Removed photo from Captures list")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessages.tryEmit(e.message ?: "Could not remove photo.")
            }
        }
    }

    fun updateCameraLightingProfile(profile: LightingProfile) {
        _cameraLighting.value = profile
    }

    fun updateRecipe(transform: (GradingRecipe) -> GradingRecipe) {
        _activeRecipe.value = transform(_activeRecipe.value)
    }

    fun applyRecipe(recipe: GradingRecipe, announce: Boolean = true) {
        _activeRecipe.value = recipe
        if (announce) {
            _statusMessages.tryEmit("Applied look: ${recipe.name}")
        }
    }

    fun applyObjectCleanup(strokes: List<CleanupStroke>) {
        val current = _originalStudioBitmap.value ?: return
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val cleaned = withContext(Dispatchers.Default) {
                ObjectCleanupEngine.removeMaskedObject(current, strokes)
            }
            cleanupHistory.addLast(current)
            if (cleanupHistory.size > 6) cleanupHistory.removeFirst()
            _canUndoCleanup.value = cleanupHistory.isNotEmpty()
            _originalStudioBitmap.value = cleaned
            _statusMessages.tryEmit("Cleaned up selected area")
        }
    }

    fun undoObjectCleanup() {
        val prev = cleanupHistory.removeLastOrNull() ?: return
        _canUndoCleanup.value = cleanupHistory.isNotEmpty()
        _originalStudioBitmap.value = prev
        _statusMessages.tryEmit("Undid edit")
    }

    fun removeBackgroundOneClick() {
        val current = _originalStudioBitmap.value
        if (current == null) {
            _statusMessages.tryEmit("Load or capture a photo first.")
            return
        }
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val result = BackgroundRemovalEngine.removeBackground(current)
            result.onSuccess { cutout ->
                cleanupHistory.addLast(current)
                if (cleanupHistory.size > 6) cleanupHistory.removeFirst()
                _canUndoCleanup.value = cleanupHistory.isNotEmpty()
                _originalStudioBitmap.value = cutout
                _statusMessages.tryEmit("Background removed (1-click)")
            }.onFailure { err ->
                _isProcessingStudio.value = false
                _statusMessages.tryEmit(err.message ?: "Could not remove background.")
            }
        }
    }

    fun applyEffectOneClick() {
        val presets = CuratedRecipes.all
        if (presets.isEmpty()) return
        val currentName = _activeRecipe.value.name
        val currentIdx = presets.indexOfFirst { it.name == currentName }
        val next = if (currentIdx < 0) presets.first() else presets[(currentIdx + 1) % presets.size]
        _activeRecipe.value = next
        _statusMessages.tryEmit("1-Click Effect: ${next.name}")
    }

    fun exportStudioPhoto(
        context: Context,
        touchMotes: List<TouchLightMote>
    ) {
        val source = _originalStudioBitmap.value
        if (source == null) {
            _statusMessages.tryEmit("Load or capture a photo first.")
            return
        }
        val recipe = _activeRecipe.value
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val exportBitmap = withContext(Dispatchers.Default) {
                val lighting = TealWarmColorEngine.analyzeLighting(source)
                val lut = TealWarmColorEngine.buildLut33(recipe, lighting)
                val graded = TealWarmColorEngine.applyLut(source, lut)
                if (recipe.particleStyle != ParticleStyle.NONE && recipe.bakeParticlesOnExport) {
                    ParticleArtEngine.bakeParticlesOntoBitmap(graded, recipe, touchMotes)
                } else {
                    graded
                }
            }
            val isCutout = MediaExportHelper.hasTransparentPixels(exportBitmap)
            val saveRes = MediaExportHelper.saveBitmapToGallery(context, exportBitmap, "JDCam_Grade")
            _isProcessingStudio.value = false
            saveRes.onSuccess { uri ->
                _lastCapturedUri.value = uri
                capturedPhotoRepository.recordCapture(
                    uriString = uri.toString(),
                    recipeName = recipe.name,
                    particleStyle = recipe.particleStyle.name
                )
                val msg = if (isCutout) {
                    "Downloaded PNG cutout to Pictures/JDCam"
                } else {
                    "Downloaded DSLR JPEG to Pictures/JDCam"
                }
                _statusMessages.tryEmit(msg)
            }.onFailure { err ->
                _statusMessages.tryEmit("Download failed: ${err.message ?: "Unknown error"}")
            }
        }
    }

    fun shareStudioPhoto(
        context: Context,
        touchMotes: List<TouchLightMote>
    ) {
        val source = _originalStudioBitmap.value
        if (source == null) {
            _statusMessages.tryEmit("Load or capture a photo first.")
            return
        }
        val recipe = _activeRecipe.value
        viewModelScope.launch {
            _isProcessingStudio.value = true
            val exportBitmap = withContext(Dispatchers.Default) {
                val lighting = TealWarmColorEngine.analyzeLighting(source)
                val lut = TealWarmColorEngine.buildLut33(recipe, lighting)
                val graded = TealWarmColorEngine.applyLut(source, lut)
                if (recipe.particleStyle != ParticleStyle.NONE && recipe.bakeParticlesOnExport) {
                    ParticleArtEngine.bakeParticlesOntoBitmap(graded, recipe, touchMotes)
                } else {
                    graded
                }
            }
            val shareRes = ShareHelper.shareBitmap(context, exportBitmap, "JDCam_Share")
            _isProcessingStudio.value = false
            shareRes.onFailure { err ->
                _statusMessages.tryEmit("Share failed: ${err.message ?: "No compatible app found"}")
            }
        }
    }

    fun shareCapturedPhotoUri(context: Context, uri: Uri) {
        ShareHelper.shareImageUri(context, uri, "Share Captured Photo")
            .onFailure { err ->
                _statusMessages.tryEmit("Share failed: ${err.message ?: "Could not open share sheet"}")
            }
    }

    fun savePhotoCopyToGallery(context: Context, uri: Uri, recipeName: String = "JD Cam") {
        viewModelScope.launch {
            val loaded = MediaExportHelper.loadBitmapFromUri(context, uri, maxEdgePx = 4096)
            loaded.onSuccess { bmp ->
                val saved = MediaExportHelper.saveBitmapToGallery(context, bmp, "JDCam_Copy")
                saved.onSuccess { newUri ->
                    _lastCapturedUri.value = newUri
                    _statusMessages.tryEmit("Downloaded copy ($recipeName) to Pictures/JDCam")
                }.onFailure { err ->
                    _statusMessages.tryEmit("Download failed: ${err.message ?: "Unknown error"}")
                }
            }.onFailure { err ->
                _statusMessages.tryEmit("Could not read photo: ${err.message ?: "Unknown error"}")
            }
        }
    }

    fun shareAppInvite(context: Context) {
        ShareHelper.shareAppInvite(context)
            .onFailure { err ->
                _statusMessages.tryEmit("Unable to open share sheet: ${err.message ?: "Unknown error"}")
            }
    }

    fun shareAppApk(context: Context) {
        if (_isAppShareBusy.value) return
        viewModelScope.launch {
            _isAppShareBusy.value = true
            val res = ShareHelper.shareAppApk(context)
            _isAppShareBusy.value = false
            res.onFailure { err ->
                _statusMessages.tryEmit("Could not share APK: ${err.message ?: "Unknown error"}")
            }
        }
    }

    fun downloadAppApk(context: Context) {
        if (_isAppShareBusy.value) return
        viewModelScope.launch {
            _isAppShareBusy.value = true
            val res = ShareHelper.saveAppApkToDownloads(context)
            _isAppShareBusy.value = false
            res.onSuccess { savedPath ->
                _statusMessages.tryEmit("Saved app APK to $savedPath")
            }.onFailure { err ->
                _statusMessages.tryEmit("APK download failed: ${err.message ?: "Unknown error"}")
            }
        }
    }

    fun savePersonalPreset(name: String, description: String) {
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) {
            _statusMessages.tryEmit("Preset name cannot be empty.")
            return
        }
        val recipe = _activeRecipe.value.copy(name = cleanName)
        viewModelScope.launch {
            try {
                localPresetRepository.savePreset(cleanName, description, recipe)
                _activeRecipe.value = recipe
                _statusMessages.tryEmit("Saved preset \"${recipe.name}\"")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessages.tryEmit(e.message ?: "Could not save preset.")
            }
        }
    }

    fun deleteLocalPreset(presetId: Long) {
        viewModelScope.launch {
            try {
                localPresetRepository.deletePreset(presetId)
                _statusMessages.tryEmit("Deleted preset")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessages.tryEmit(e.message ?: "Could not delete preset.")
            }
        }
    }

    fun notifyUser(message: String) {
        _statusMessages.tryEmit(message)
    }

    private companion object {
        const val TAG = "AureliaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
