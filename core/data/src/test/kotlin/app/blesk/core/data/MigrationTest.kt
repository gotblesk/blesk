package app.blesk.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.db.BleskDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MigrationTest {

    @Test
    fun `version 1 database opens as the current version with its data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1.forEach(db::execSQL)
            db.execSQL("INSERT INTO conversations VALUES ('bob','Direct','Боб','EndToEnd','bob',1,0,0,NULL,'привет')")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, BleskDatabase::class.java, file.path)
            .addMigrations(BleskDatabase.MIGRATION_1_2, BleskDatabase.MIGRATION_2_3, BleskDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            assertEquals("привет", room.conversations().get("bob")?.previewText)
            assertEquals(null, room.groups().get("missing"))
        }
        room.close()
    }

    private companion object {
        val V1 = listOf(
            "CREATE TABLE conversations (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `protection` TEXT NOT NULL, " +
                "`peer` TEXT, `updatedAt` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
                "`previewAuthor` TEXT, `previewText` TEXT, PRIMARY KEY(`id`))",
            "CREATE TABLE messages (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `authorId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`sentAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `replyToId` TEXT, `reactions` TEXT NOT NULL, " +
                "`edited` INTEGER NOT NULL, `attachment` TEXT, PRIMARY KEY(`id`))",
            "CREATE INDEX `index_messages_conversationId_sentAt` ON messages (`conversationId`, `sentAt`)",
            "CREATE TABLE contacts (`username` TEXT NOT NULL, `name` TEXT NOT NULL, `bio` TEXT NOT NULL, `identityKey` TEXT, " +
                "`verified` INTEGER NOT NULL, PRIMARY KEY(`username`))",
            "CREATE TABLE outbox (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipient` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                "`ephemeral` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE signal_state (`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `record` BLOB NOT NULL, `extra` INTEGER NOT NULL, " +
                "PRIMARY KEY(`kind`, `name`))",
        )
    }
}
