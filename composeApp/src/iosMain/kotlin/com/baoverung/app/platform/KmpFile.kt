package com.baoverung.app.platform

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Source
import platform.Foundation.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class KmpFile actual constructor(actual val path: String) {
    private val fileManager = NSFileManager.defaultManager

    actual val exists: Boolean get() = fileManager.fileExistsAtPath(path)
    
    actual val length: Long get() {
        val attributes = fileManager.attributesOfItemAtPath(path, null)
        return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }

    actual val nameWithoutExtension: String get() = path.toPath().name.substringBeforeLast(".")
    actual val parent: String? get() = path.toPath().parent?.toString()

    actual fun readBytes(): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        val bytes: CPointer<ByteVar>? = data.bytes?.reinterpret()
        return if (bytes != null) {
            ByteArray(data.length.toInt()).apply {
                for (i in indices) {
                    this[i] = bytes[i]
                }
            }
        } else ByteArray(0)
    }

    actual fun readText(): String {
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: ""
    }

    actual fun openSource(): Source = FileSystem.SYSTEM.source(path.toPath())

    actual fun readAt(position: Long, buffer: ByteArray): Int {
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return -1
        handle.seekToOffset(position.toULong(), null)
        val data = handle.readDataOfLength(buffer.size.toULong())
        if (data.length == 0uL) return 0
        
        val bytes: CPointer<ByteVar>? = data.bytes?.reinterpret()
        if (bytes == null) return 0
        
        for (i in 0 until data.length.toInt()) {
            buffer[i] = bytes[i]
        }
        handle.closeFile()
        return data.length.toInt()
    }
}

actual fun getFileSystemSeparator(): String = "/"
