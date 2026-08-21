package com.baoverung.app.gis

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Standardized SAF set staging for MapInfo files.
 */
object MapInfoSafHelper {
    private const val TAG = "MapInfoSafHelper"

    data class StagingResult(
        val tabPath: String? = null,
        val missingExtensions: List<String> = emptyList(),
        val error: String? = null
    )

    fun stageFileSet(context: Context, tabUri: Uri): StagingResult {
        val tabFile = DocumentFile.fromSingleUri(context, tabUri) ?: return StagingResult(error = "Không thể mở tệp .TAB")
        
        // Requirement: For MapInfo sets, we need access to the folder. 
        // If selected via OpenDocument, parentFile is often null.
        val parentDir = tabFile.parentFile 
        if (parentDir == null) {
            return StagingResult(error = "Vui lòng sử dụng tính năng 'QUÉT MÁY' hoặc chọn tệp từ một thư mục đã cấp quyền để nạp đủ bộ 4 tệp MapInfo.")
        }
        
        val baseName = tabFile.name?.substringBeforeLast(".") ?: ""
        val requiredExts = listOf("tab", "dat", "map", "id")
        val stagedFiles = mutableMapOf<String, File>()
        val missing = mutableListOf<String>()

        val storageDir = File(context.filesDir, "imported_gis/tab_sets/${System.currentTimeMillis()}")
        storageDir.mkdirs()

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

        return if (stagedFiles.size >= 4) {
            StagingResult(tabPath = stagedFiles["tab"]?.absolutePath)
        } else {
            StagingResult(missingExtensions = missing)
        }
    }

    fun stageFileSetFromFile(context: Context, tabFile: File): StagingResult {
        val parent = tabFile.parentFile ?: return StagingResult(error = "Thư mục không hợp lệ")
        val base = tabFile.nameWithoutExtension
        val required = listOf("tab", "dat", "map", "id")
        val missing = mutableListOf<String>()
        
        for (ext in required) {
            if (!File(parent, "$base.$ext").exists() && !File(parent, "$base.${ext.uppercase()}").exists()) {
                missing.add(".$ext")
            }
        }
        
        return if (missing.isEmpty()) {
            StagingResult(tabPath = tabFile.absolutePath)
        } else {
            StagingResult(missingExtensions = missing)
        }
    }

    fun cleanup(stagedPath: String?) {
        if (stagedPath == null) return
        try {
            val file = File(stagedPath)
            file.parentFile?.deleteRecursively()
        } catch (e: Exception) {}
    }
}
