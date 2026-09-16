package com.markq.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.markq.R
import com.markq.core.MarkPlace

enum class MapApp(
    val packageName: String,
    val missingMessageRes: Int,
) {
    Baidu(MarkPlace.BAIDU_PACKAGE, R.string.maps_not_installed_baidu),
    Amap(MarkPlace.AMAP_PACKAGE, R.string.maps_not_installed_amap),
}

sealed class MapLaunch {
    data object NoLocation : MapLaunch()
    data object Opened : MapLaunch()
    data class Missing(val app: MapApp) : MapLaunch()
}

object MapsLauncher {
    fun installed(context: Context, packageName: String): Boolean =
        runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)

    fun open(
        context: Context,
        app: MapApp,
        latitude: Double?,
        longitude: Double?,
        placeName: String?,
    ): MapLaunch {
        if (!MarkPlace.hasFix(latitude, longitude)) return MapLaunch.NoLocation
        if (!installed(context, app.packageName)) return MapLaunch.Missing(app)
        val uris = when (app) {
            MapApp.Amap -> MarkPlace.amapUris(latitude!!, longitude!!, placeName)
            MapApp.Baidu -> MarkPlace.baiduUris(latitude!!, longitude!!, placeName)
        }
        for (uri in uris) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(app.packageName)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return MapLaunch.Opened
        }
        return MapLaunch.Missing(app)
    }
}
