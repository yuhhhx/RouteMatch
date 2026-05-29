package com.example.routematch

import kotlin.math.*

// SECURITY: All matching is done locally, no network requests
object MatchAlgorithm {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Haversine formula: great-circle distance between two GPS coordinates.
     */
    fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Bearing from point A to point B (degrees from north).
     */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val y = sin(dLng) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Calculate a match score [0, 1] for whether an order is on the rider's way.
     *
     * @param currentLat  Rider's current latitude
     * @param currentLng  Rider's current longitude
     * @param pickupLat   Order pickup latitude
     * @param pickupLng   Order pickup longitude
     * @param deliveryLat Order delivery latitude
     * @param deliveryLng Order delivery longitude
     * @param maxDetourMeters  Maximum acceptable detour distance
     * @param maxAngleDegrees  Maximum acceptable direction angle difference
     * @return Score 0.0 to 1.0 (1.0 = perfectly on the way)
     */
    fun calculateMatchScore(
        currentLat: Double, currentLng: Double,
        pickupLat: Double, pickupLng: Double,
        deliveryLat: Double, deliveryLng: Double,
        maxDetourMeters: Double = 1000.0,
        maxAngleDegrees: Double = 45.0
    ): Double {
        // Calculate distances
        val distToPickup = haversineDistance(currentLat, currentLng, pickupLat, pickupLng)
        val distPickupToDelivery = haversineDistance(pickupLat, pickupLng, deliveryLat, deliveryLng)
        val distDirect = haversineDistance(currentLat, currentLng, deliveryLat, deliveryLng)

        // Detour = extra distance when taking this order vs going directly
        val detour = (distToPickup + distPickupToDelivery) - distDirect

        // Distance score: 0 detour = 1.0, max detour = 0.0
        val distanceScore = when {
            detour <= 0 -> 1.0
            detour >= maxDetourMeters -> 0.0
            else -> 1.0 - (detour / maxDetourMeters)
        }

        // Direction score: check if pickup is in the same general direction as destination
        val bearingToDest = bearing(currentLat, currentLng, deliveryLat, deliveryLng)
        val bearingToPickup = bearing(currentLat, currentLng, pickupLat, pickupLng)
        val angleDiff = abs(bearingToDest - bearingToPickup)
        val normalizedAngle = minOf(angleDiff, 360.0 - angleDiff)

        val directionScore = when {
            normalizedAngle <= maxAngleDegrees -> 1.0 - (normalizedAngle / maxAngleDegrees) * 0.5
            else -> 0.5.coerceAtMost(1.0 - (normalizedAngle - maxAngleDegrees) / (180.0 - maxAngleDegrees))
        }

        // Also consider order value (could be weighted in)
        // Weighted combination: 60% distance, 40% direction
        return distanceScore * 0.6 + directionScore.coerceIn(0.0, 1.0) * 0.4
    }

    /**
     * Quick check if an order is even worth considering before full calculation.
     */
    fun isWithinRange(
        currentLat: Double, currentLng: Double,
        pickupLat: Double, pickupLng: Double,
        maxRadiusMeters: Double = 5000.0
    ): Boolean {
        return haversineDistance(currentLat, currentLng, pickupLat, pickupLng) <= maxRadiusMeters
    }
}
