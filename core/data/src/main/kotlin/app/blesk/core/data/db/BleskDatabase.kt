package app.blesk.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        OutboxEntity::class,
        SignalStateEntity::class,
        GroupEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class BleskDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun contacts(): ContactDao
    abstract fun outbox(): OutboxDao
    abstract fun signal(): SignalStateDao
    abstract fun groups(): GroupDao

    companion object {
        private const val NAME = "blesk.db"

        /** Groups and rooms: a group table, room channels under their room, sealed fan-out in the outbox. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN parentId TEXT")
                db.execSQL("ALTER TABLE outbox ADD COLUMN sealedType INTEGER")
                db.execSQL("ALTER TABLE outbox ADD COLUMN sealedBody TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `groups` (`id` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`keyHolders` TEXT NOT NULL, `keyEpoch` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** Bots: inline buttons under their messages; a warning when someone's key changes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN buttons TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN keyChanged INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Drafts, disappearing messages, pinned messages, forwards. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN timerSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinnedMessageId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN expiresAt INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN forwardedFrom TEXT")
            }
        }

        /** Opens the SQLCipher database with [passphrase]; the caller gets it from Android Keystore. */
        fun open(context: Context, passphrase: ByteArray): BleskDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BleskDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
