package com.baoverung.app.gis

import android.content.Context
import android.util.Log
import com.baoverung.app.data.model.GisFeature
import java.io.File

/**
 * Standardized MapInfo Parser implementing professional GIS logic.
 * Enforces the 4-file set rule, TCVN3 encoding conversion, and VN-2000 reprojection.
 */
object GdalMapInfoParser {
    private const val TAG = "GdalMapInfoParser"

    /**
     * Strictly enforces MapInfo Native 4-file set rule.
     */
    private fun checkFileSet(tabFile: File): Boolean {
        val parent = tabFile.parentFile ?: return false
        val base = tabFile.nameWithoutExtension
        val exts = listOf("tab", "dat", "map", "id")
        val missing = mutableListOf<String>()
        
        for (ext in exts) {
            val variations = listOf("$base.$ext", "$base.${ext.uppercase()}", "${base.lowercase()}.$ext", "${base.uppercase()}.$ext")
            if (variations.none { File(parent, it).exists() }) missing.add(".$ext")
        }
        
        if (missing.isNotEmpty()) {
            Log.e(TAG, "Thiếu bộ tệp MapInfo cho ${tabFile.name}: ${missing.joinToString(", ")}")
            return false
        }
        return true
    }

    suspend fun parseMapInfoWithNative(
        context: Context,
        tabUri: android.net.Uri,
        layerId: Long,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onFeatureParsed: suspend (GisFeature) -> Unit
    ) {
        // 1. Stage files from SAF to internal cache for GDAL access
        val result = MapInfoSafHelper.stageFileSet(context, tabUri)
        val stagedPath = result.tabPath
        
        if (stagedPath == null) {
            val errorMsg = if (result.missingExtensions.isNotEmpty()) {
                "Thiếu bộ tệp MapInfo: ${result.missingExtensions.joinToString(", ")}"
            } else {
                result.error ?: "Không thể nạp bộ tệp MapInfo từ SAF."
            }
            Log.e(TAG, errorMsg)
            return
        }

        try {
            // 2. Try Native GDAL Parser first
            if (NativeGdalParser.isAvailable()) {
                val features = NativeGdalParser.readMapInfoSafe(stagedPath)
                if (features != null && features.isNotEmpty()) {
                    features.forEach { feature ->
                        onFeatureParsed(feature.copy(layerId = layerId))
                    }
                    Log.d(TAG, "Đã nạp ${features.size} đối tượng từ native GDAL")
                    return
                } else if (features != null && features.isEmpty()) {
                    Log.w(TAG, "Native GDAL trả về 0 đối tượng, chuyển sang bộ nạp Kotlin dự phòng.")
                }
            }
            
            // 3. Fallback to Kotlin Parser
            Log.i(TAG, "Sử dụng bộ nạp Kotlin cho: $stagedPath")
            val file = File(stagedPath)
            CoordinateSystemConverter.initialize(context)
            MapInfoTabParser.parseTabFileStreaming(
                file = file,
                layerId = layerId,
                centralMeridian = centralMeridian,
                zoneDegrees = zoneDegrees,
                onFeatureParsed = onFeatureParsed
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi nạp dữ liệu MapInfo", e)
            // If failed during processing, we might want to cleanup the staged directory
            MapInfoSafHelper.cleanup(stagedPath)
        }
    }

    suspend fun parseMapInfoStreaming(
        context: Context,
        file: File,
        layerId: Long,
        centralMeridian: Double = 107.75,
        zoneDegrees: Int = 3,
        onProgress: (suspend (Int, Int) -> Unit)? = null,
        onFeatureParsed: suspend (GisFeature) -> Unit
    ) {
        // 1. Enforce file integrity
        if (!checkFileSet(file)) {
            Log.e(TAG, "Yêu cầu phải có đủ bộ tệp MapInfo (.TAB, .DAT, .MAP, .ID) trong cùng thư mục.")
            throw Exception("Thiếu bộ tệp MapInfo Native.")
        }

        // 2. Initialize coordinate system
        CoordinateSystemConverter.initialize(context)

        // 3. Try Native GDAL Parser first (if available and high performance needed)
        if (NativeGdalParser.isAvailable()) {
            try {
                Log.d(TAG, "Thử nạp bằng Native GDAL cho: ${file.name}")
                val features = NativeGdalParser.readMapInfoSafe(file.absolutePath)
                if (features != null && features.isNotEmpty()) {
                    features.forEachIndexed { index, feature ->
                        if (index % 100 == 0) onProgress?.invoke(index + 1, features.size)
                        onFeatureParsed(feature.copy(layerId = layerId))
                    }
                    Log.i(TAG, "Đã nạp ${features.size} đối tượng thành công bằng Native GDAL.")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native GDAL không khả dụng hoặc lỗi: ${e.message}. Chuyển sang bộ nạp Kotlin.")
            }
        }

        // 4. Primary Professional Kotlin Engine
        Log.i(TAG, "Sử dụng Professional Kotlin Engine cho: ${file.name}")
        MapInfoTabParser.parseTabFileStreaming(
            file = file,
            layerId = layerId,
            centralMeridian = centralMeridian,
            zoneDegrees = zoneDegrees,
            skipConversion = false,
            onProgress = onProgress,
            onFeatureParsed = onFeatureParsed
        )
    }
}
