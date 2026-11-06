package dev.inspecto.android.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * Photo waiting to be uploaded to media-service and attached to an observation in core-service.
 * The row is deleted once the photo is attached; until then the compressed file stays in the app's
 * private storage.
 *
 * @property id local identifier, also the `Idempotency-Key` of the upload: a repeated upload after a
 *     lost answer returns the photo created by the first one
 * @property observationId observation on the server; null while the observation exists only on the
 *     device (T-308), and the photo waits for it
 * @property photoId photo in media-service once uploaded; the attachment is then the only step left
 * @property errorCode API code of a failure that repeating cannot fix (for example
 *     `PHOTO_LIMIT_EXCEEDED`); such a photo waits for the inspector to retry or remove it
 */
@Entity(tableName = "pending_photo")
data class PendingPhoto(
    @PrimaryKey val id: String,
    val propertyId: String,
    val inspectionId: String,
    val observationId: String?,
    val filePath: String,
    val photoId: String? = null,
    val errorCode: String? = null,
    val createdAt: Long,
)

@Dao
interface PendingPhotoDao {
    @Insert
    suspend fun insert(photo: PendingPhoto)

    /** Photos that can be sent now, oldest first. */
    @Query("SELECT * FROM pending_photo WHERE observationId IS NOT NULL AND errorCode IS NULL ORDER BY createdAt")
    suspend fun ready(): List<PendingPhoto>

    @Query("SELECT * FROM pending_photo WHERE inspectionId = :inspectionId ORDER BY createdAt")
    fun observeInspection(inspectionId: String): Flow<List<PendingPhoto>>

    @Query("SELECT * FROM pending_photo WHERE id = :id")
    suspend fun find(id: String): PendingPhoto?

    @Query("UPDATE pending_photo SET photoId = :photoId WHERE id = :id")
    suspend fun uploaded(
        id: String,
        photoId: String,
    )

    @Query("UPDATE pending_photo SET errorCode = :errorCode WHERE id = :id")
    suspend fun setError(
        id: String,
        errorCode: String?,
    )

    @Query("DELETE FROM pending_photo WHERE id = :id")
    suspend fun delete(id: String)
}

/** Local data of the app: work that must survive a restart until the server has it. */
@Database(entities = [PendingPhoto::class], version = 1)
abstract class InspectoDatabase : RoomDatabase() {
    abstract fun pendingPhotos(): PendingPhotoDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): InspectoDatabase = Room.databaseBuilder(context, InspectoDatabase::class.java, "inspecto.db").build()

    @Provides
    fun providePendingPhotoDao(database: InspectoDatabase): PendingPhotoDao = database.pendingPhotos()
}
