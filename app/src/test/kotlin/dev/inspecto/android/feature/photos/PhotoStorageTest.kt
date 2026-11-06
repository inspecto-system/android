package dev.inspecto.android.feature.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.inspecto.android.core.db.InspectoDatabase
import dev.inspecto.android.core.db.PendingPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compression on the device and the Room queue (Robolectric with real image codecs and SQLite). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun shouldShrinkTurnUprightAndDropMetadata() {
        val shot = folder.newFile("shot.jpg")
        shot.outputStream().use {
            Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        ExifInterface(shot.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(55.79, 49.12)
            setAttribute(ExifInterface.TAG_MODEL, "Phone X")
            saveAttributes()
        }
        val target = folder.root.resolve("queue/out.jpg")

        PhotoCompressor().compress(shot, target)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.path, bounds)
        assertEquals(1920, bounds.outWidth)
        assertEquals(2560, bounds.outHeight)
        val exif = ExifInterface(target.path)
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals(0, exif.rotationDegrees)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldRejectNonImage() {
        val notImage = folder.newFile("note.jpg").apply { writeText("not an image") }
        PhotoCompressor().compress(notImage, folder.root.resolve("out.jpg"))
    }

    @Test
    fun shouldDecodeWithLargestSampleKeepingLongSide() {
        assertEquals(1, PhotoCompressor.sampleSize(4000, 3000))
        assertEquals(2, PhotoCompressor.sampleSize(8160, 6120))
        assertEquals(1, PhotoCompressor.sampleSize(1200, 1600))
    }

    @Test
    fun shouldQueueReadyPhotosOldestFirst() =
        runBlocking {
            val db =
                Room
                    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InspectoDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            val dao = db.pendingPhotos()
            dao.insert(photo("late", createdAt = 3))
            dao.insert(photo("early", createdAt = 1))
            dao.insert(photo("local", createdAt = 2).copy(observationId = null))
            dao.insert(photo("failed", createdAt = 0))
            dao.setError("failed", "PHOTO_LIMIT_EXCEEDED")
            dao.uploaded("early", "server-id")

            assertEquals(listOf("early", "late"), dao.ready().map { it.id })
            assertEquals("server-id", dao.find("early")?.photoId)
            assertEquals(listOf("failed", "early", "local", "late"), dao.observeInspection("i").first().map { it.id })
            dao.delete("late")
            assertNull(dao.find("late"))
            db.close()
        }

    private fun photo(
        id: String,
        createdAt: Long,
    ) = PendingPhoto(id, "p", "i", "o", "$id.jpg", createdAt = createdAt)
}
