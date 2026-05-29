package com.example.routematch

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log

// SECURITY: Single-shot GPS positioning only, no continuous tracking
class LocationHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Manual override position (set via Settings)
    private var manualLat: Double? = null
    private var manualLng: Double? = null

    /**
     * Set a manual fixed location (overrides GPS).
     */
    fun setManualLocation(lat: Double, lng: Double) {
        manualLat = lat
        manualLng = lng
        Log.d(TAG, "Manual location set: $lat, $lng")
    }

    /**
     * Clear manual location and return to GPS-based positioning.
     */
    fun clearManualLocation() {
        manualLat = null
        manualLng = null
    }

    /**
     * Whether a manual location override is currently active.
     */
    fun isManualLocationSet(): Boolean = manualLat != null && manualLng != null

    /**
     * Get current location (single-shot).
     * Priority: manual override > GPS provider > Network provider
     */
    @SuppressLint("MissingPermission")
    fun getLocation(callback: (Location?) -> Unit) {
        // Priority 1: Manual override
        if (manualLat != null && manualLng != null) {
            val location = Location("manual").apply {
                latitude = manualLat!!
                longitude = manualLng!!
                accuracy = 1.0f // Assume accurate
                time = System.currentTimeMillis()
            }
            callback(location)
            return
        }

        // Priority 2: Check for available providers
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { locationManager.isProviderEnabled(it) }

        if (providers.isEmpty()) {
            Log.w(TAG, "No location providers available")
            callback(null)
            return
        }

        // Priority 3: Use last known location (fast, may be stale)
        for (provider in providers) {
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 120_000) {
                // Use if less than 2 minutes old
                callback(lastKnown)
                return
            }
        }

        // Priority 4: Request a single fresh location update
        // SECURITY: requestSingleUpdate - not continuous tracking
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                callback(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        // Try GPS first
        if (providers.contains(LocationManager.GPS_PROVIDER)) {
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                listener,
                Looper.getMainLooper()
            )
            // Set a fallback timeout: if GPS doesn't respond quickly, try network
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                locationManager.removeUpdates(listener)
                if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        listener,
                        Looper.getMainLooper()
                    )
                } else {
                    callback(null)
                }
            }, 8000) // 8 second GPS timeout
        } else {
            // Fallback to network provider
            locationManager.requestSingleUpdate(
                LocationManager.NETWORK_PROVIDER,
                listener,
                Looper.getMainLooper()
            )
        }
    }

    /**
     * Check if location services (GPS or Network) are enabled.
     */
    fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
