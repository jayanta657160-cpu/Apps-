package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.CapturedPhotoEntity
import com.example.imaging.MediaExportHelper
import com.example.model.ParticleStyle
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.ElevatedTealSurface
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory
import com.example.viewmodel.AureliaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CapturesScreen(
    viewModel: AureliaViewModel,
    onOpenCamera: () -> Unit,
    onOpenStudio: () -> Unit
) {
    val context = LocalContext.current
    val capturedPhotos by viewModel.capturedPhotos.collectAsStateWithLifecycle()
    val isAppShareBusy by viewModel.isAppShareBusy.collectAsStateWithLifecycle()
    var selectedPhoto by remember { mutableStateOf<CapturedPhotoEntity?>(null) }
    var showShareAppDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianTeal)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Captured Photos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmIvory
                )
                Text(
                    text = "${capturedPhotos.size} saved in Pictures/JDCam",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showShareAppDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElevatedTealSurface)
                        .testTag("captures_share_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(id = R.string.share_app),
                        tint = ChampagneGold
                    )
                }

                Button(
                    onClick = onOpenCamera,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = ObsidianTeal
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("captures_open_camera_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Capture", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (capturedPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = DeepStudioTeal,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ChampagneGold.copy(alpha = 0.14f))
                                .border(1.dp, ChampagneGold.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = ChampagneGold,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "No Captured Photos Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = WarmIvory,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap the shutter button in Camera to capture a Teal & Warm photo and save it directly to your Gallery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onOpenCamera,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneGold,
                                contentColor = ObsidianTeal
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("captures_empty_camera_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Take a Photo")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(capturedPhotos, key = { it.id }) { item ->
                    CapturedPhotoGridTile(
                        photo = item,
                        onClick = { selectedPhoto = item }
                    )
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        CapturedPhotoViewerDialog(
            photo = photo,
            onDismiss = { selectedPhoto = null },
            onShare = {
                viewModel.shareCapturedPhotoUri(context, Uri.parse(photo.uriString))
            },
            onDownloadCopy = {
                viewModel.savePhotoCopyToGallery(context, Uri.parse(photo.uriString), photo.recipeName)
            },
            onEditInStudio = {
                viewModel.loadPickedPhoto(context, Uri.parse(photo.uriString), photo.recipeName)
                selectedPhoto = null
                onOpenStudio()
            },
            onRemove = {
                viewModel.deleteCapturedPhoto(photo.id)
                selectedPhoto = null
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
private fun CapturedPhotoGridTile(
    photo: CapturedPhotoEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.uriString) {
        val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull() ?: return@LaunchedEffect
        MediaExportHelper.loadBitmapFromUri(context, uri, maxEdgePx = 640)
            .onSuccess { bitmap = it }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(18.dp))
            .background(ElevatedTealSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("captured_photo_tile_${photo.id}")
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = "Captured photo ${photo.recipeName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = LuminousTeal.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ObsidianTeal.copy(alpha = 0.9f))
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = photo.recipeName,
                    style = MaterialTheme.typography.labelLarge,
                    color = WarmIvory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ParticleStyle.fromRaw(photo.particleStyle).label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChampagneGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CapturedPhotoViewerDialog(
    photo: CapturedPhotoEntity,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownloadCopy: () -> Unit,
    onEditInStudio: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.uriString) {
        val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull() ?: return@LaunchedEffect
        MediaExportHelper.loadBitmapFromUri(context, uri, maxEdgePx = 1800)
            .onSuccess { bitmap = it }
    }

    val dateLabel = remember(photo.capturedAtMillis) {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            .format(Date(photo.capturedAtMillis))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianTeal,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = photo.recipeName,
                            style = MaterialTheme.typography.titleLarge,
                            color = WarmIvory
                        )
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampagneGold
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElevatedTealSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remove from Captures",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElevatedTealSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close preview",
                            tint = WarmIvory
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DeepStudioTeal)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val loaded = bitmap
                    if (loaded != null) {
                        Image(
                            bitmap = loaded.asImageBitmap(),
                            contentDescription = "Captured photo preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Photo is saved in Pictures/JDCam",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDownloadCopy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("capture_viewer_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(id = R.string.download_photo),
                            tint = LuminousTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.download_photo),
                            color = WarmIvory
                        )
                    }

                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LuminousTeal,
                            contentColor = ObsidianTeal
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("capture_viewer_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(id = R.string.share_photo),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.share_photo))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onEditInStudio,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = ObsidianTeal
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("capture_viewer_edit_studio_button")
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Color Studio")
                }
            }
        }
    }
}
