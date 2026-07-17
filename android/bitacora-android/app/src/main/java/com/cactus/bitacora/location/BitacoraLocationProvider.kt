package com.cactus.bitacora.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BitacoraLocationProvider(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun getCurrentLocation(): LocationSnapshot {
        check(hasPermission()) { "Se necesita permiso de ubicación para registrar el GPS" }
        check(isLocationEnabled()) { "La ubicación está desactivada" }

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        continuation.resumeWithException(
                            IllegalStateException("No fue posible obtener una ubicación actual")
                        )
                    } else {
                        continuation.resume(
                            LocationSnapshot(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                altitude = location.altitude.takeIf { location.hasAltitude() },
                                timestamp = location.time,
                                provider = location.provider ?: "fused"
                            )
                        )
                    }
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }
}
