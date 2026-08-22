package com.baoverung.app.platform

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Source
import java.io.File
import java.io.RandomAccessFile

actual class KmpFile actual constructor(actual val path: String) {
    private val file = File(path)

    actual val exists: Boolean get() = file.exists()
    actual val length: Long get() = file.length()
    actual val nameWithoutExtension: String get() = file.nameWithoutExtension
    actual val parent: String? get() = file.parent

    actual fun readBytes(): ByteArray = file.readBytes()
    actual fun readText(): String = file.readText()

    actual fun openSource(): Source = FileSystem.SYSTEM.source(path.toPath())

    actual fun readAt(position: Long, buffer: ByteArray): Int {
        if (!exists) return -1
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(position)
            return raf.read(buffer)
        }
    }
}

actual fun getFileSystemSeparator(): String = File.separator
