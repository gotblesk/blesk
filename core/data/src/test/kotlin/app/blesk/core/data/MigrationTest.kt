package app.blesk.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            .addMigrations(*UP_TO_12.toTypedArray(), BleskDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            assertEquals("привет", room.conversations().get("bob")?.previewText)
            assertEquals(null, room.groups().get("missing"))
            assertEquals(null, room.groups().leftGroup("missing"))
        }
        room.close()
    }

    @Test
    fun `a version 12 group keeps its state and leaves the holders of its old key behind`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-12.db").apply { parentFile?.mkdirs(); delete() }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(file.path)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V1.forEach(db::execSQL)
                        UP_TO_12.forEach { it.migrate(db) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL("INSERT INTO `groups` VALUES ('g', '{\"id\":\"g\"}', '[\"bob\",\"carol\"]', 3)")
        }

        val room = Room.databaseBuilder(context, BleskDatabase::class.java, file.path)
            .addMigrations(BleskDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            assertEquals("{\"id\":\"g\"}", room.groups().get("g")?.state)
            assertNull(room.groups().senderKey("g", ""))
            assertEquals(emptyList<Any>(), room.groups().releasable("g", Int.MAX_VALUE))
            assertNull(room.groups().voiceKey("g", "c"))
        }
        val columns = room.openHelper.readableDatabase.query("PRAGMA table_info(`groups`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertEquals(listOf("id", "state"), columns)
        room.close()
    }

    @Test
    fun `pinned chats keep their order and only chats with sound return from the archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
        val dao = room.conversations()
        runBlocking {
            listOf("a", "b", "c").forEachIndexed { i, id ->
                dao.upsert(ConversationEntity(id, "Direct", id, "EndToEnd", id, updatedAt = i.toLong()))
                dao.setPinned(id, true, dao.lastPinRank() + 1)
            }
            dao.reorderPinned(listOf("c", "a", "b"))
            assertEquals(listOf("c", "a", "b"), dao.observeAll().first().map { it.id })

            dao.upsert(ConversationEntity("loud", "Direct", "loud", "EndToEnd", "loud", updatedAt = 0, archived = true))
            val quiet = ConversationEntity("quiet", "Direct", "quiet", "EndToEnd", "quiet", 0, archived = true)
            dao.upsert(quiet.copy(mutedUntil = Long.MAX_VALUE))
            dao.returnFromArchive("loud", now = 1)
            dao.returnFromArchive("quiet", now = 1)
            assertEquals(false, dao.get("loud")?.archived)
            assertEquals(true, dao.get("quiet")?.archived)
        }
        room.close()
    }

    private companion object {
        val UP_TO_12 = listOf(
            BleskDatabase.MIGRATION_1_2,
            BleskDatabase.MIGRATION_2_3,
            BleskDatabase.MIGRATION_3_4,
            BleskDatabase.MIGRATION_4_5,
            BleskDatabase.MIGRATION_5_6,
            BleskDatabase.MIGRATION_6_7,
            BleskDatabase.MIGRATION_7_8,
            BleskDatabase.MIGRATION_8_9,
            BleskDatabase.MIGRATION_9_10,
            BleskDatabase.MIGRATION_10_11,
            BleskDatabase.MIGRATION_11_12,
        )

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
