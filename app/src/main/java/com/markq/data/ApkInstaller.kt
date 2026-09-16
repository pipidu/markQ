package com.markq.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.net.Uri
import com.markq.data.local.CacheJanitor
import java.io.File

object ApkInstaller {
    const val ACTION_INSTALL_STATUS = "com.markq.action.INSTALL_STATUS"

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownSourcesIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun install(context: Context, apk: File) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val sessionId = installer.createSession(params)
        var session: PackageInstaller.Session? = null
        try {
            session = installer.openSession(sessionId)
            session.openWrite("markq", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callbackIntent = Intent(app, InstallStatusReceiver::class.java).apply {
                action = ACTION_INSTALL_STATUS
            }
            val callback = PendingIntent.getBroadcast(app, sessionId, callbackIntent, flags)
            session.commit(callback.intentSender)
        } catch (e: Exception) {
            try {
                session?.abandon()
            } catch (_: Exception) {
            }
            throw e
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {
            }
        }
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmationIntent(intent) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            CacheJanitor.deleteUpdateApks(context)
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }
}
