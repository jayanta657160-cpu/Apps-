package com.example.imaging

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareHelper {

    private const val SHARED_CACHE_DIR = "shared"
    private const val STALE_CACHE_MS = 60L * 60L * 1000L

    fun shareImageUri(
        context: Context,
        uri: Uri,
        chooserTitle: String = "Share Photo"
    ): Result<Unit> = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, sendIntent, chooserTitle)
    }

    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "JDCam_Share",
        chooserTitle: String = "Share Graded Photo"
    ): Result<Unit> = runCatching {
        val shareUri = withContext(Dispatchers.IO) {
            val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val exportAsPng = MediaExportHelper.hasTransparentPixels(safeBitmap)
            val sharedDir = ensureSharedDir(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val ext = if (exportAsPng) "png" else "jpg"
            val outFile = File(sharedDir, "${prefix}_$timestamp.$ext")
            FileOutputStream(outFile).use { out ->
                val format = if (exportAsPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (exportAsPng) 100 else 96
                if (!safeBitmap.compress(format, quality, out)) {
                    error("Could not prepare image for sharing.")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val mime = if (exportAsPng) "image/png" else "image/jpeg"
            uri to mime
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = shareUri.second
            putExtra(Intent.EXTRA_STREAM, shareUri.first)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            launchChooser(context, sendIntent, chooserTitle)
        }
    }

    fun shareAppInvite(context: Context): Result<Unit> = runCatching {
        val shareText = buildString {
            appendLine("Try JD Cam — Teal & Warm Cinema Camera")
            appendLine("• Real-time Teal & Warm wedding LUT preview & Sobel Focus Peaking")
            appendLine("• Burst capture, 3s & 10s self-timer, tap-to-focus & pinch-to-zoom")
            append("• 1-click Background Removal, 1-click Cinematic Effects & DSLR Gallery Export")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "JD Cam — Teal & Warm Cinema Camera")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        launchChooser(context, sendIntent, "Share JD Cam")
    }

    suspend fun shareAppApk(context: Context): Result<Unit> = runCatching {
        val apkUri = withContext(Dispatchers.IO) {
            val sourcePath = context.applicationInfo.sourceDir
                ?: error("App package path is unavailable on this device.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                error("Cannot read installed app package.")
            }
            val sharedDir = ensureSharedDir(context)
            val apkName = "JDCam_v${BuildConfig.VERSION_NAME}.apk"
            val targetFile = File(sharedDir, apkName)
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_SUBJECT, "JD Cam v${BuildConfig.VERSION_NAME} APK")
            putExtra(
                Intent.EXTRA_TEXT,
                "Install JD Cam (Teal & Warm Cinema Camera) using the attached APK."
            )
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            launchChooser(context, sendIntent, "Share JD Cam APK")
        }
    }

    suspend fun saveAppApkToDownloads(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo.sourceDir
                ?: error("App package path is unavailable on this device.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                error("Cannot read installed app package.")
            }
            val filename = "JDCam_v${BuildConfig.VERSION_NAME}.apk"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/JDCam"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create Downloads entry.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("Could not open output stream for APK.")
                    val done = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null)
                    throw t
                }
                "Downloads/JDCam/$filename"
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "JDCam").apply { mkdirs() }
                val targetFile = File(targetDir, filename)
                sourceFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/vnd.android.package-archive"),
                    null
                )
                "Downloads/JDCam/$filename"
            }
        }
    }

    private fun ensureSharedDir(context: Context): File {
        val dir = File(context.cacheDir, SHARED_CACHE_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_CACHE_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
        return dir
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
