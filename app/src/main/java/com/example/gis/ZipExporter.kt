package com.baoverung.app.gis

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility for packaging Forestry survey reports into a single ZIP file.
 */
object ZipExporter {

    fun createZipFile(files: List<File>, zipFilePath: String): File? {
        if (files.isEmpty()) return null
        
        val zipFile = File(zipFilePath)
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val usedNames = mutableSetOf<String>()
                
                files.forEach { file ->
                    if (file.exists() && file.isFile) {
                        var entryName = file.name
                        // Handle duplicate names
                        if (usedNames.contains(entryName)) {
                            entryName = "${System.currentTimeMillis()}_${entryName}"
                        }
                        usedNames.add(entryName)

                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
