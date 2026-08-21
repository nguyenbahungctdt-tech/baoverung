package com.baoverung.app.util

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

object GeometryUtils {
    /**
     * Calculates the minimum distance from point P to line segment AB
     */
    fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
        val l2 = (b.x - a.x).pow(2) + (b.y - a.y).pow(2)
        if (l2 == 0f) return sqrt((p.x - a.x).pow(2) + (p.y - a.y).pow(2))
        
        var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
        t = max(0f, min(1f, t))
        
        val projection = Offset(
            a.x + t * (b.x - a.x),
            a.y + t * (b.y - a.y)
        )
        
        return sqrt((p.x - projection.x).pow(2) + (p.y - projection.y).pow(2))
    }

    fun isPointInPolygon(p: Offset, poly: List<Offset>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            if (((poly[i].y > p.y) != (poly[j].y > p.y)) &&
                (p.x < (poly[j].x - poly[i].x) * (p.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Douglas-Peucker simplification algorithm
     * Reduces the number of points in a curve while keeping its shape.
     */
    fun simplifyPoints(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points

        var maxDist = 0f
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = distToSegment(points[i], points[0], points[end])
            if (d > maxDist) {
                index = i
                maxDist = d
            }
        }

        return if (maxDist > epsilon) {
            val left = simplifyPoints(points.subList(0, index + 1), epsilon)
            val right = simplifyPoints(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points[0], points[end])
        }
    }
}
