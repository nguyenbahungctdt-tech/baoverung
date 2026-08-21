package com.baoverung.app.gis

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Standardized SAF set staging for QGIS Shapefiles.
 * Copies .shp, .dbf, .shx, .prj, and .cpg as a set.
 */
object ShapefileSafHelper {
    private const val TAG = "ShapefileSafHelper"

    data class StagingResult(
        val shpPath: String? = null,
        val missingExtensions: List<String> = emptyList(),
        val error: String? = null
    )

    fun stageFileSet(context: Context, shpUri: Uri): StagingResult {
        val shpFile = DocumentFile.fromSingleUri(context, shpUri) ?: return StagingResult(error = "Không thể tìm tệp .SHP")
        val parentDir = shpFile.parentFile ?: return StagingResult(error = "Không thể tìm thư mục chứa tệp")
        
        val baseName = shpFile.name?.substringBeforeLast(".") ?: ""
        val requiredExts = listOf("shp", "dbf", "shx")
        val optionalExts = listOf("prj", "cpg")
        val stagedFiles = mutableMapOf<String, File>()
        val missing = mutableListOf<String>()

        val storageDir = File(context.filesDir, "imported_gis/shp_sets/${System.currentTimeMillis()}")
        storageDir.mkdirs()

        // 1. Copy required files
        for (ext in requiredExts) {
            val fileName = "$baseName.$ext"
            val targetDoc = parentDir.findFile(fileName) ?: parentDir.findFile(fileName.uppercase())
            
            if (targetDoc != null) {
                val destFile = File(storageDir, fileName)
                try {
                    context.contentResolver.openInputStream(targetDoc.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    stagedFiles[ext] = destFile
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi sao chép $fileName: ${e.message}")
                }
            } else {
                missing.add(".$ext")
            }
        }

        // 2. Copy optional files (PRJ, CPG)
        for (ext in optionalExts) {
            val fileName = "$baseName.$ext"
            val targetDoc = parentDir.findFile(fileName) ?: parentDir.findFile(fileName.uppercase())
            if (targetDoc != null) {
                val destFile = File(storageDir, fileName)
                try {
                    context.contentResolver.openInputStream(targetDoc.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        return if (stagedFiles.size >= 3) {
            StagingResult(shpPath = stagedFiles["shp"]?.absolutePath)
        } else {
            StagingResult(missingExtensions = missing)
        }
    }

    fun stageFileSetFromFile(context: Context, shpFile: File): StagingResult {
        val parent = shpFile.parentFile ?: return StagingResult(error = "Thư mục không hợp lệ")
        val base = shpFile.nameWithoutExtension
        val required = listOf("shp", "dbf", "shx")
        val missing = mutableListOf<String>()
        
        for (ext in required) {
            if (!File(parent, "$base.$ext").exists() && !File(parent, "$base.${ext.uppercase()}").exists()) {
                missing.add(".$ext")
            }
        }
        
        return if (missing.isEmpty()) {
            StagingResult(shpPath = shpFile.absolutePath)
        } else {
            StagingResult(missingExtensions = missing)
        }
    }
}
