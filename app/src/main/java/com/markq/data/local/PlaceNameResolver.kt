package com.markq.data.local

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.markq.LocaleHelper
import com.markq.data.remote.NominatimGeocoder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlaceNameResolver(
    private val appContext: Context,
    private val nominatim: NominatimGeocoder,
) {
    suspend fun resolve(latitude: Double, longitude: Double): String? {
        val fromNominatim = withTimeoutOrNull(8_000) {
            runCatching { nominatim.reverse(latitude, longitude) }.getOrNull()
        }
        if (!fromNominatim.isNullOrBlank()) return fromNominatim
        return withTimeoutOrNull(2_500) {
            runCatching { androidGeocoder(appContext, latitude, longitude) }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun androidGeocoder(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, LocaleHelper.appLocale)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1) { list ->
                    if (cont.isActive) cont.resume(list.firstOrNull())
                }
            }
        } else {
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }
        return formatAndroidAddress(address)
    }

    companion object {
        internal fun formatAndroidAddress(address: Address?): String? {
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
}
