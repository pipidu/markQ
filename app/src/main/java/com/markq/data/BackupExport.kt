package com.markq.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.markq.R
import com.markq.data.local.AttachmentStore
import com.markq.data.local.MarkDatabase
import com.markq.data.remote.RemoteEntry
import com.markq.data.remote.RemoteTemplate
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object BackupExport {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    suspend fun write(
        context: Context,
        db: MarkDatabase,
        files: AttachmentStore,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val outFile = File(dir, "MarkQ-backup-${stamp.format(LocalDateTime.now())}.zip")
        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            for (row in db.entries().listActive()) {
                val body = json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(row.toModel()))
                    .toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("entries/${row.entry.id}.json"))
                zip.write(body)
                zip.closeEntry()
                for (att in row.attachments) {
                    val blob = files.locate(row.entry.id, att.id, att.sha256)
                    if (!blob.exists()) continue
                    zip.putNextEntry(ZipEntry("files/${row.entry.id}/${att.id}"))
                    blob.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            for (row in db.templates().listActive()) {
                val body = json.encodeToString(RemoteTemplate.serializer(), RemoteTemplate.from(row.toModel()))
                    .toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("templates/${row.id}.json"))
                zip.write(body)
                zip.closeEntry()
            }
        }
        if (!outFile.exists() || outFile.length() == 0L) {
            error(context.getString(R.string.error_export_failed))
        }
        outFile
    }

    fun share(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.export_share_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
