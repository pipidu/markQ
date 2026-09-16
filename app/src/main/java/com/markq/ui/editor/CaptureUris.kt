package com.markq.ui.editor

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

object CaptureUris {
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun createTempJpeg(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        dir.listFiles()?.forEach { existing -> existing.delete() }
        val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        return file to uri
    }

    fun grantToCameraApps(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
            context.grantUriPermission(info.activityInfo.packageName, uri, flags)
        }
    }

    fun revoke(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.revokeUriPermission(uri, flags) }
    }

    fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }
}

class TakePictureToCache : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent {
        val intent = super.createIntent(context, input)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", input)
        CaptureUris.grantToCameraApps(context, input)
        return intent
    }
}
