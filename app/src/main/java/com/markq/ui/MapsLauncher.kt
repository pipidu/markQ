package com.markq.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.markq.R
import com.markq.core.MarkPlace

object MapsLauncher {
    fun open(context: Context, latitude: Double?, longitude: Double?, placeName: String?): Boolean {
        if (!MarkPlace.hasFix(latitude, longitude)) return false
        val view = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(MarkPlace.geoUri(latitude!!, longitude!!, placeName)),
        )
        val chooser = Intent.createChooser(view, context.getString(R.string.location_open_maps)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
