package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.simplemobiletools.commons.extensions.getCompressionFormat
import com.simplemobiletools.commons.extensions.getMyFileUri
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.extension
import com.simplemobiletools.smsmessenger.extensions.getExtensionFromMimeType
import java.io.File
import java.io.FileOutputStream

/**
 * Compress image to a given size based on
 * [Compressor](https://github.com/zetbaitsu/Compressor/)
 * */
class ImageCompressor(private val context: Context) {
    companion object {
        private const val TAG = "ImageCompressor"
    }

    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        mkdirs()
    }

    fun compressImage(byteArray: ByteArray, mimeType: String, compressSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            try {
                Log.d(TAG, "Attempting to compress image of length: ${byteArray.size} of mimetype=$mimeType to size=$compressSize")
                var destinationFile = File(outputDirectory, System.currentTimeMillis().toString().plus(mimeType.getExtensionFromMimeType()))
                Log.d(TAG, "compressImage: Saving file to: $destinationFile")
                destinationFile.writeBytes(byteArray)
                Log.d(TAG, "Written file to: $destinationFile")
                val constraint = SizeConstraint(compressSize)
                Log.d(TAG, "Starting compression...")
                while (constraint.isSatisfied(destinationFile).not()) {
                    destinationFile = constraint.satisfy(destinationFile)
                    Log.d(TAG, "Compressed, new size is ${destinationFile.length()}")
                }

                Log.d(TAG, "Compression done, new size is ${destinationFile.length()}")
                callback.invoke(context.getMyFileUri(destinationFile))
            } catch (e: Exception) {
                Log.e(TAG, "compressImage: ", e)
                callback.invoke(null)
            }
        }
    }

    private fun overWrite(imageFile: File, bitmap: Bitmap, format: Bitmap.CompressFormat = imageFile.path.getCompressionFormat(), quality: Int = 100): File {
        val result = if (format == imageFile.path.getCompressionFormat()) {
            imageFile
        } else {
            File("${imageFile.absolutePath.substringBeforeLast(".")}.${format.extension()}")
        }
        imageFile.delete()
        saveBitmap(bitmap, result, format, quality)
        return result
    }

    private fun saveBitmap(bitmap: Bitmap, destination: File, format: Bitmap.CompressFormat = destination.path.getCompressionFormat(), quality: Int = 100) {
        destination.parentFile?.mkdirs()
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(destination.absolutePath)
            bitmap.compress(format, quality, fileOutputStream)
        } finally {
            fileOutputStream?.run {
                flush()
                close()
            }
        }
    }

    private fun loadBitmap(imageFile: File) = BitmapFactory.decodeFile(imageFile.absolutePath).run {
        determineImageRotation(imageFile, this)
    }

    private fun determineImageRotation(imageFile: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(imageFile.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
        val matrix = Matrix()
        when (orientation) {
            6 -> matrix.postRotate(90f)
            3 -> matrix.postRotate(180f)
            8 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private inner class SizeConstraint(
        private val maxFileSize: Long,
        private val stepSize: Int = 10,
        private val maxIteration: Int = 10,
        private val minQuality: Int = 10
    ) {
        private var iteration: Int = 0

        fun isSatisfied(imageFile: File): Boolean {
            return imageFile.length() <= maxFileSize || iteration >= maxIteration
        }

        fun satisfy(imageFile: File): File {
            iteration++
            val quality = (100 - iteration * stepSize).takeIf { it >= minQuality } ?: minQuality
            return overWrite(imageFile, loadBitmap(imageFile), quality = quality)
        }
    }

}
