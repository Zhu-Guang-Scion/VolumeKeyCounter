package com.volumekey.counter.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 定位快照：国产 ROM 无 Play Service 场景下用 LocationManager 取最优一次定位，
 * Geocoder 逆地理编码（不可用时优雅降级为 null，水印只显示坐标）。
 */
object LocationSnapshot {

    data class Fix(val lat: Double, val lng: Double, val address: String?)

    /** 立即返回缓存最优位置；最多等待 freshMs 毫秒期望新定位 */
    @SuppressLint("MissingPermission")
    suspend fun bestFix(context: Context, freshMs: Long = 1200): Fix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (l != null && (best == null || l.time > best.time)) best = l
        }
        // 若缓存太旧，短暂等待一次新定位（GPS 首锁慢，仅作为尽力而为）
        if (best == null || System.currentTimeMillis() - best.time > 60_000) {
            val fresh = withTimeoutOrNull(freshMs) {
                awaitSingleUpdate(context, providers)
            }
            if (fresh != null && (best == null || fresh.time > best.time)) best = fresh
        }
        val loc = best ?: return null
        val address = reverseGeocode(context, loc.latitude, loc.longitude)
        return Fix(loc.latitude, loc.longitude, address)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleUpdate(context: Context, providers: List<String>): Location? =
        suspendCancellableCoroutine { cont ->
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var done = false
            lateinit var listener: LocationListener
            listener = LocationListener { l ->
                if (!done) {
                    done = true
                    runCatching { providers.forEach { lm.removeUpdates(listener) } }
                    cont.resume(l)
                }
            }
            var launched = false
            for (p in providers) {
                val ok = runCatching {
                    lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                    true
                }.getOrDefault(false)
                if (ok) launched = true
            }
            if (!launched) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                runCatching { providers.forEach { lm.removeUpdates(listener) } }
            }
        }

    /** 逆地理编码：Geocoder 在部分 ROM/模拟器上不可用，返回 null 即降级 */
    fun reverseGeocode(context: Context, lat: Double, lng: Double): String? = try {
        val geocoder = Geocoder(context, Locale.CHINA)
        @Suppress("DEPRECATION")
        val addrs = geocoder.getFromLocation(lat, lng, 1)
        val a = addrs?.firstOrNull() ?: return null
        // 省+市+区+街道级拼接；过长截断
        val parts = listOfNotNull(a.adminArea, a.locality, a.subLocality, a.thoroughfare ?: a.featureName)
        val joined = parts.filter { it.isNotBlank() }.joinToString("")
        joined.ifBlank { a.getAddressLine(0) }.take(40).ifBlank { null }
    } catch (_: Exception) {
        null
    }

    fun formatLatLon(lat: Double, lng: Double): String {
        val la = String.format(Locale.US, "%.5f", lat)
        val lo = String.format(Locale.US, "%.5f", lng)
        return "经纬度 $la, $lo"
    }
}
