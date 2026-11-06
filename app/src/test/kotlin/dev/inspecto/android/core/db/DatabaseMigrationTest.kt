package dev.inspecto.android.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Photos queued by version 1 of the app survive the update to the offline drafts (T-308). The old
 * database is built from the exported schema 1; opening it with Room runs the migration and checks
 * that the result matches schema 2 exactly (Room refuses to open a database that does not).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun shouldKeepQueuedPhotosAndAddDraftTables() {
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            schemaStatements(version = 1).forEach(old::execSQL)
            old.execSQL(
                "INSERT INTO pending_photo (id, propertyId, inspectionId, observationId, filePath, createdAt) " +
                    "VALUES ('p', 'prop', 'insp', 'obs', 'p.jpg', 1)",
            )
            old.version = 1
        }

        val db =
            Room
                .databaseBuilder(context, InspectoDatabase::class.java, NAME)
                .addMigrations(InspectoDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        runBlocking {
            val photo = checkNotNull(db.pendingPhotos().find("p"))
            assertEquals("obs", photo.observationId)
            assertNull(photo.observationKey)
            db.pendingActions().insert(
                PendingAction(
                    inspectionId = "insp",
                    type = "SET_RESULT",
                    targetId = "i",
                    payload = "{}",
                    createdAt = 2,
                ),
            )
            assertEquals(1L, db.pendingActions().next()?.seq)
        }
        db.close()
    }

    /** Tables of an exported schema and Room's bookkeeping, as Room creates them. */
    private fun schemaStatements(version: Int): List<String> {
        val text =
            context.assets
                .open("dev.inspecto.android.core.db.InspectoDatabase/$version.json")
                .bufferedReader()
                .use { it.readText() }
        val database =
            Json
                .parseToJsonElement(text)
                .jsonObject
                .getValue("database")
                .jsonObject
        val tables =
            database.getValue("entities").jsonArray.flatMap { entity ->
                val table = entity.jsonObject
                val name = table.getValue("tableName").jsonPrimitive.content
                val indices =
                    table["indices"]?.jsonArray.orEmpty().map {
                        it.jsonObject
                            .getValue(
                                "createSql",
                            ).jsonPrimitive.content
                    }
                (
                    listOf(
                        table.getValue("createSql").jsonPrimitive.content,
                    ) + indices
                ).map { it.replace("\${TABLE_NAME}", name) }
            }
        return tables + database.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
