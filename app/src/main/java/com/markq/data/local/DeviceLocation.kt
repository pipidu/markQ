package com.markq.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.markq.LocaleHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
)

object DeviceLocation {
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun peek(context: Context): LocationFix? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = lastKnown(lm) ?: return null
        return LocationFix(loc.latitude, loc.longitude, placeName = null)
    }

    suspend fun current(context: Context): LocationFix? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val last = lastKnown(lm)
        val fresh = withTimeoutOrNull(6_000) { awaitFresh(context, lm) }
        val loc = choose(fresh, last) ?: return@withContext null
        val name = withTimeoutOrNull(2_500) { placeName(context, loc.latitude, loc.longitude) }
        LocationFix(loc.latitude, loc.longitude, name)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? {
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }
        return candidates.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFresh(context: Context, lm: LocationManager): Location? {
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val done = AtomicBoolean(false)
            fun finish(location: Location?) {
                if (!done.compareAndSet(false, true)) return
                if (cont.isActive) cont.resume(location)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(context),
                ) { loc -> finish(loc) }
                cont.invokeOnCancellation { }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        finish(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    finish(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation {
                    runCatching { lm.removeUpdates(listener) }
                }
            }
        }
    }

    private fun choose(fresh: Location?, last: Location?): Location? {
        if (fresh == null) return last
        if (last == null) return fresh
        return if (fresh.time >= last.time) fresh else last
    }

    @Suppress("DEPRECATION")
    private suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, LocaleHelper.appLocale)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1) { list ->
                    if (cont.isActive) cont.resume(list.firstOrNull())
                }
            }
        } else {
            runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
        }
        return formatAddress(address)
    }

    internal fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val parts = listOfNotNull(
            address.subLocality,
            address.thoroughfare,
            address.featureName,
            address.locality,
            address.adminArea,
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parts.take(3).joinToString(" ").ifBlank { null }
    }
}
