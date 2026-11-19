package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.ElevatedTealSurface
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory

@Composable
fun ShareAppDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onShareApk: () -> Unit,
    onDownloadApk: () -> Unit,
    onShareInvite: () -> Unit,
    onPermissionDenied: (String) -> Unit
) {
    val context = LocalContext.current
    val legacyDownloadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onDownloadApk()
        } else {
            onPermissionDenied("Storage permission is needed on Android 9 and below to save the APK to Downloads.")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DeepStudioTeal,
        titleContentColor = WarmIvory,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(id = R.string.share_app_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmIvory
                )
                Text(
                    text = "JD Cam v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Send the installable APK directly to friends, save a copy to Downloads, or share an app invite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isBusy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = ChampagneGold,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Preparing app package...",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampagneGold
                        )
                    }
                }

                ShareAppOptionCard(
                    icon = Icons.Filled.Android,
                    accentColor = ChampagneGold,
                    title = stringResource(id = R.string.share_app_apk),
                    subtitle = "Send APK via WhatsApp, Nearby Share, Bluetooth or Files",
                    enabled = !isBusy,
                    onClick = onShareApk,
                    testTag = "share_app_apk_button"
                )

                ShareAppOptionCard(
                    icon = Icons.Filled.Download,
                    accentColor = LuminousTeal,
                    title = stringResource(id = R.string.download_app_apk),
                    subtitle = "Save JDCam_v${BuildConfig.VERSION_NAME}.apk to Downloads/JDCam",
                    enabled = !isBusy,
                    onClick = {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            legacyDownloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            onDownloadApk()
                        }
                    },
                    testTag = "download_app_apk_button"
                )

                ShareAppOptionCard(
                    icon = Icons.Filled.Share,
                    accentColor = WarmIvory,
                    title = stringResource(id = R.string.share_app_invite),
                    subtitle = "Send app features & quick message via any chat app",
                    enabled = !isBusy,
                    onClick = onShareInvite,
                    testTag = "share_app_invite_button"
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                modifier = Modifier.testTag("share_app_close_button")
            ) {
                Text("Close", color = ChampagneGold)
            }
        }
    )
}

@Composable
private fun ShareAppOptionCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = ElevatedTealSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = WarmIvory
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
