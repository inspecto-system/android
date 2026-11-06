package dev.inspecto.android.feature.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares a camera shot for upload on the device: limits the long side, turns the pixels upright
 * by the EXIF orientation and encodes a new JPEG. The new file has no metadata, so neither the
 * location nor the camera model leaves the phone (media-service removes EXIF too, ADR-025).
 */
class PhotoCompressor
    @Inject
    constructor() {
        /**
         * Writes the compressed copy of [source] to [target].
         *
         * @throws IllegalArgumentException when [source] is not an image
         */
        fun compress(
            source: File,
            target: File,
        ) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
            val decoded =
                requireNotNull(
                    BitmapFactory.decodeFile(
                        source.path,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) },
                    ),
                ) { "Not an image" }
            val upright = upright(scaled(decoded), orientation(source))
            target.parentFile?.mkdirs()
            target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            upright.recycle()
        }

        private fun scaled(bitmap: Bitmap): Bitmap {
            val longSide = max(bitmap.width, bitmap.height)
            if (longSide <= MAX_SIDE) return bitmap
            val factor = MAX_SIDE.toFloat() / longSide
            val result = bitmap.scale((bitmap.width * factor).roundToInt(), (bitmap.height * factor).roundToInt())
            bitmap.recycle()
            return result
        }

        private fun upright(
            bitmap: Bitmap,
            degrees: Int,
        ): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return result
        }

        private fun orientation(source: File): Int = ExifInterface(source.path).rotationDegrees

        internal companion object {
            /**
             * Long side sent to the server: media-service keeps the original and makes a ≈1280 px
             * preview from it (ADR-025), so 2560 px keeps details of cracks visible at a fraction of
             * the size of a 12–50 MP shot.
             */
            const val MAX_SIDE = 2560
            const val JPEG_QUALITY = 85

            /** Largest power of two that keeps the long side at least [MAX_SIDE] (cheap decoding). */
            fun sampleSize(
                width: Int,
                height: Int,
            ): Int {
                var sample = 1
                while (max(width, height) / (sample * 2) >= MAX_SIDE) {
                    sample *= 2
                }
                return sample
            }
        }
    }
