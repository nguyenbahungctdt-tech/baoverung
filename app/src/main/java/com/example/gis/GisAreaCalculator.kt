package com.baoverung.app.gis

import com.baoverung.app.data.model.GpsPoint
import kotlin.math.*

/**
 * Utility for GIS area and distance calculations using Great Circle and Shoelace algorithms.
 */
object GisAreaCalculator {

    private const val EARTH_RADIUS = 6378137.0 // Meters

    /**
     * Calculates distance between two WGS84 points in meters.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

    /**
     * Calculates total path length in meters.
     */
    fun calculatePathLength(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += calculateDistance(points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude)
        }
        return total
    }

    /**
     * Calculates area of a WGS84 polygon in square meters.
     */
    fun calculatePolygonArea(points: List<GpsPoint>): Double {
        if (points.size < 3) return 0.0
        var area = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            area += Math.toRadians(p2.longitude - p1.longitude) * (2 + sin(Math.toRadians(p1.latitude)) + sin(Math.toRadians(p2.latitude)))
        }
        area = area * EARTH_RADIUS * EARTH_RADIUS / 2.0
        return abs(area)
    }

    /**
     * Calculates perimeter of a WGS84 polygon in meters.
     */
    fun calculatePolygonPerimeter(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        var perimeter = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            perimeter += calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        return perimeter
    }

    /**
     * Calculates the geometric center of a list of points.
     */
    fun calculateCentroid(points: List<GpsPoint>): GpsPoint {
        if (points.isEmpty()) return GpsPoint(0.0, 0.0)
        val lat = points.map { it.latitude }.average()
        val lon = points.map { it.longitude }.average()
        return GpsPoint(lat, lon)
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360) % 360
    }

    fun formatDistance(meters: Double, unit: String = "Auto"): String {
        return when {
            unit == "km" || (unit == "Auto" && meters >= 1000) -> String.format("%.2f km", meters / 1000.0)
            else -> String.format("%.0f m", meters)
        }
    }

    fun formatArea(sqMeters: Double, unit: String = "Auto"): String {
        return when {
            unit == "ha" || (unit == "Auto" && sqMeters >= 10000) -> String.format("%.2f ha", sqMeters / 10000.0)
            else -> String.format("%.0f m²", sqMeters)
        }
    }
}
