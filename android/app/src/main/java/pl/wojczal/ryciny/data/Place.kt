package pl.wojczal.ryciny.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LatLon(val lat: Double, val lon: Double, val fromGps: Boolean)

/** Where the frame is: the phone's fix when allowed, else the coordinates in the settings (Gdańsk by default). */
class Place(private val context: Context, private val settings: SettingsStore, scope: CoroutineScope) {
    private val state = MutableStateFlow(fallback())
    val flow: StateFlow<LatLon> = state
    val value: LatLon get() = state.value

    init {
        scope.launch {
            while (true) {
                refresh()
                delay(15 * 60_000L)
            }
        }
    }

    suspend fun refresh() {
        val s = settings.value
        if (!s.useGps || !allowed()) {
            state.value = fallback()
            return
        }
        val fix = current() ?: lastKnown()
        state.value = fix?.let { LatLon(it.latitude, it.longitude, true) } ?: fallback()
    }

    private fun fallback() = settings.value.let { LatLon(it.lat, it.lon, false) }

    private fun allowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val manager get() = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? =
        manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun current(): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
            }
        }
    }
}
