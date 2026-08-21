package com.baoverung.app.gis

import com.baoverung.app.data.model.GisFeature
import com.baoverung.app.data.model.GisShapeType
import com.baoverung.app.data.model.GpsPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight GeoJSON/JSON Parser for Forestry.
 */
object GeoJsonParser {

    fun parseGeoJson(file: File, layerId: Long = 0): List<GisFeature> {
        val features = mutableListOf<GisFeature>()
        try {
            val content = file.readText()
            val root = JSONObject(content)
            
            if (root.getString("type") == "FeatureCollection") {
                val featuresArray = root.getJSONArray("features")
                for (i in 0 until featuresArray.length()) {
                    val featObj = featuresArray.getJSONObject(i)
                    parseFeature(featObj, layerId, features)
                }
            } else if (root.getString("type") == "Feature") {
                parseFeature(root, layerId, features)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return features
    }

    private fun parseFeature(featObj: JSONObject, layerId: Long, outList: MutableList<GisFeature>) {
        val geometry = featObj.optJSONObject("geometry") ?: return
        val properties = featObj.optJSONObject("properties") ?: JSONObject()
        
        val attrs = mutableMapOf<String, String>()
        properties.keys().forEach { key ->
            attrs[key] = properties.optString(key)
        }

        fun createFeature(id: String, type: GisShapeType, points: List<GpsPoint>): GisFeature {
            var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
            for (p in points) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            return GisFeature(id, layerId, type, points, attrs, minLat, maxLat, minLon, maxLon)
        }

        val type = geometry.getString("type")
        when (type) {
            "Point" -> {
                val coords = geometry.getJSONArray("coordinates")
                val pt = GpsPoint(coords.getDouble(1), coords.getDouble(0))
                outList.add(createFeature("geojson_${outList.size + 1}", GisShapeType.POINT, listOf(pt)))
            }
            "LineString" -> {
                val pts = parseCoordsArray(geometry.getJSONArray("coordinates"))
                outList.add(createFeature("geojson_${outList.size + 1}", GisShapeType.LINE, pts))
            }
            "Polygon" -> {
                val rings = geometry.getJSONArray("coordinates")
                if (rings.length() > 0) {
                    val pts = parseCoordsArray(rings.getJSONArray(0))
                    outList.add(createFeature("geojson_${outList.size + 1}", GisShapeType.POLYGON, pts))
                }
            }
            "MultiPolygon" -> {
                val polys = geometry.getJSONArray("coordinates")
                for (p in 0 until polys.length()) {
                    val rings = polys.getJSONArray(p)
                    if (rings.length() > 0) {
                        val pts = parseCoordsArray(rings.getJSONArray(0))
                        outList.add(createFeature("geojson_${outList.size + 1}", GisShapeType.POLYGON, pts))
                    }
                }
            }
        }
    }

    private fun parseCoordsArray(arr: JSONArray): List<GpsPoint> {
        val list = mutableListOf<GpsPoint>()
        for (i in 0 until arr.length()) {
            val pt = arr.getJSONArray(i)
            list.add(GpsPoint(pt.getDouble(1), pt.getDouble(0)))
        }
        return list
    }
}
