package com.example.lanman.ui.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Small helpers for acting on a locally stored file: open it, share it, or copy it into a
 * user-visible folder so it survives uninstalling the app.
 */
object MediaActions {

    fun openFile(context: Context, path: String, mimeType: String): Boolean {
        val uri = resolveUri(context, path) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(Intent.createChooser(intent, "Open file")) }.isSuccess
    }

    fun shareFile(context: Context, path: String, mimeType: String): Boolean {
        val uri = resolveUri(context, path) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(Intent.createChooser(intent, "Share file")) }.isSuccess
    }

    /** Copies the file into the public Downloads collection. Returns false if it could not. */
    fun saveToDownloads(context: Context, path: String, fileName: String, mimeType: String): Boolean {
        val source = resolveUri(context, path) ?: return false
        val safeName = fileName.ifBlank { "download" }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LANMAN")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(target)?.use { out ->
                    resolver.openInputStream(source)?.use { input -> input.copyTo(out) }
                        ?: throw IllegalStateException("Cannot read source file")
                } ?: throw IllegalStateException("Cannot write to Downloads")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(target, values, null, null)
                true
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, "LANMAN")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, safeName)
                context.contentResolver.openInputStream(source)?.use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                } ?: return false
                true
            }
        }.getOrDefault(false)
    }

    /** Saves a bitmap into Pictures/LANMAN so the user can keep or forward it. */
    fun saveBitmapToPictures(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LANMAN")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(target)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw IllegalStateException("Cannot write image")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(target, values, null, null)
                true
            } else {
                val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dir = File(pictures, "LANMAN")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun resolveUri(context: Context, path: String): Uri? {
        if (path.isBlank()) return null
        if (path.startsWith("content://")) return Uri.parse(path)
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
}
