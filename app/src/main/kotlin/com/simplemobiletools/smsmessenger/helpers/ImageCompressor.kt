package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.simplemobiletools.commons.extensions.getCompressionFormat
import com.simplemobiletools.commons.extensions.getMyFileUri
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.extension
import com.simplemobiletools.smsmessenger.extensions.getExtensionFromMimeType
import com.simplemobiletools.smsmessenger.extensions.getFileSizeFromUri
import com.simplemobiletools.smsmessenger.extensions.isImageMimeType
import java.io.File
import java.io.FileOutputStream

/**
 * Compress image to a given size based on
 * [Compressor](https://github.com/zetbaitsu/Compressor/)
 * */
class ImageCompressor(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val outputDirectory = File(context.cacheDir, "compressed").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    fun compressImage(uri: Uri, compressSize: Long, callback: (compressedFileUri: Uri?) -> Unit) {
        ensureBackgroundThread {
            try {
                val fileSize = context.getFileSizeFromUri(uri)
                if (fileSize > compressSize) {
                    val mimeType = contentResolver.getType(uri)!!
                    if (mimeType.isImageMimeType()) {
                        val byteArray = contentResolver.openInputStream(uri)?.readBytes()!!
                        var destinationFile = File(outputDirectory, System.currentTimeMillis().toString().plus(mimeType.getExtensionFromMimeType()))
                        destinationFile.writeBytes(byteArray)
                        val sizeConstraint = SizeConstraint(compressSize)
                        val bitmap = loadBitmap(destinationFile)

                        // if image weight > * 2 targeted size: cut down resolution by 2
                        if (fileSize > 2 * compressSize) {
                            val resConstraint = ResolutionConstraint(bitmap.width / 2, bitmap.height / 2)
                            while (resConstraint.isSatisfied(destinationFile).not()) {
                                destinationFile = resConstraint.satisfy(destinationFile)
                            }
                        }
                        // do compression
                        while (sizeConstraint.isSatisfied(destinationFile).not()) {
                            destinationFile = sizeConstraint.satisfy(destinationFile)
                        }
                        callback.invoke(context.getMyFileUri(destinationFile))
                    } else {
                        callback.invoke(null)
                    }
                } else {
                    //no need to compress since the file is less than the compress size
                    callback.invoke(uri)
                }
            } catch (e: Exception) {
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
            // If size requirement is not met and maxIteration is reached
            if (iteration >= maxIteration && imageFile.length() >= maxFileSize) {
                throw Exception("Unable to compress image to targeted size")
            }
            return imageFile.length() <= maxFileSize
        }

        fun satisfy(imageFile: File): File {
            iteration++
            val quality = (100 - iteration * stepSize).takeIf { it >= minQuality } ?: minQuality
            return overWrite(imageFile, loadBitmap(imageFile), quality = quality)
        }
    }

    private inner class ResolutionConstraint(private val width: Int, private val height: Int) {

        private fun decodeSampledBitmapFromFile(imageFile: File, reqWidth: Int, reqHeight: Int): Bitmap {
            return BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(imageFile.absolutePath, this)

                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

                inJustDecodeBounds = false
                BitmapFactory.decodeFile(imageFile.absolutePath, this)
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        fun isSatisfied(imageFile: File): Boolean {
            return BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(imageFile.absolutePath, this)
                calculateInSampleSize(this, width, height) <= 1
            }
        }

        fun satisfy(imageFile: File): File {
            return decodeSampledBitmapFromFile(imageFile, width, height).run {
                determineImageRotation(imageFile, this).run {
                    overWrite(imageFile, this)
                }
            }
        }
    }
}
