package com.baoverung.app.platform

import okio.Source

/**
 * Platform-independent file access for GIS binary parsing.
 */
expect class KmpFile(path: String) {
    val path: String
    val exists: Boolean
    val length: Long
    val nameWithoutExtension: String
    val parent: String?

    fun readBytes(): ByteArray
    fun readText(): String
    
    // For binary streaming
    fun openSource(): Source
    
    // For random access (needed by Shapefile/MapInfo)
    fun readAt(position: Long, buffer: ByteArray): Int
}

expect fun getFileSystemSeparator(): String
