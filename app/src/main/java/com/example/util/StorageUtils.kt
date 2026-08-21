package com.baoverung.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StorageUtils {

    /**
     * Saves a bitmap to app-private storage and returns the absolute path.
     */
    fun saveImageToInternalStorage(context: Context, bitmap: Bitmap, fileName: String, quality: Int = 95): String? {
        return try {
            val imagesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "BaoVeRung")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val imageFile = File(imagesDir, fileName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            imageFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a bitmap to the public Pictures/BaoVeRung folder and indexes it.
     */
    fun saveImageToPublicStorage(context: Context, bitmap: Bitmap, fileName: String, quality: Int = 95): String? {
        val relativePath = "Pictures/BaoVeRung"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            
            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let { targetUri ->
                try {
                    contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(targetUri, contentValues, null, null)
                    
                    return targetUri.toString()
                } catch (e: Exception) {
                    contentResolver.delete(targetUri, null, null)
                    return null
                }
            }
            return null
        } else {
            try {
                val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BaoVeRung")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, fileName)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                }
                // Trigger media scanner to make it visible in Gallery and other apps
                android.media.MediaScannerConnection.scanFile(context, arrayOf(imageFile.absolutePath), arrayOf("image/jpeg"), null)
                return imageFile.absolutePath
            } catch (e: Exception) {
                return null
            }
        }
    }

    /**
     * Copies a file from app-private cache to public storage
     */
    fun promoteTempPhotoToPublic(context: Context, tempUri: Uri, finalFileName: String): String? {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(tempUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                return saveImageToPublicStorage(context, bitmap, finalFileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Deletes a file safely
     */
    fun deleteFile(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {}
    }
}
