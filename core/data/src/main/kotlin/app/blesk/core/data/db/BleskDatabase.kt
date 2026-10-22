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
        MessageFtsEntity::class,
        LeftGroupEntity::class,
        GroupSenderKeyEntity::class,
        HeldGroupMessageEntity::class,
        VoiceKeyEntity::class,
    ],
    version = 13,
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

        /** As Room creates it for [MessageFtsEntity]; kept identical so the schema check passes after migrating. */
        private val FTS_SCHEMA = listOf(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(`text` TEXT NOT NULL, tokenize=unicode61, content=`messages`)",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_UPDATE BEFORE UPDATE ON `messages` " +
                "BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_DELETE BEFORE DELETE ON `messages` " +
                "BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_UPDATE AFTER UPDATE ON `messages` " +
                "BEGIN INSERT INTO `messages_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_INSERT AFTER INSERT ON `messages` " +
                "BEGIN INSERT INTO `messages_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
        )

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

        /** Archive, mute and «mark as unread» for chats. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN mutedUntil INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN markedUnread INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** A full-text index over messages; filled once from what is already there. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                FTS_SCHEMA.forEach(db::execSQL)
                db.execSQL("INSERT INTO messages_fts(messages_fts) VALUES ('rebuild')")
            }
        }

        /** Pinned chats keep the order you give them; «Избранное» stays first until you move it. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinRank INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE conversations SET pinRank = CASE WHEN kind = 'Saved' THEN 0 ELSE 1 END WHERE pinned = 1")
            }
        }

        /** Who is a contact rather than a stranger who wrote first, and who is blocked. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN added INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE contacts ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Link cards under messages. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN link TEXT")
            }
        }

        /** Profile photos and links of the people you talk to. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN photo TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN links TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Where unfinished downloads come from, so they can be cancelled, retried or started later. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN remote TEXT")
            }
        }

        /** Groups this phone let go of, so an update already on its way does not bring them back. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `left_groups` (`id` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`byUs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * Our sender keys, one per audience of a group instead of one per group, group messages waiting for their
         * state, and the keys of closed voice channels. Who held the old key is not carried over: named after its group
         * and epoch, it may have gone to people not on record, so each sender hands a fresh one out once.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `groups_new` (`id` TEXT NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `groups_new` (`id`, `state`) SELECT `id`, `state` FROM `groups`")
                db.execSQL("DROP TABLE `groups`")
                db.execSQL("ALTER TABLE `groups_new` RENAME TO `groups`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_sender_keys` (`groupId` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`distributionId` TEXT NOT NULL, `epoch` INTEGER NOT NULL, `holders` TEXT NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `scope`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `held_group_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`groupId` TEXT NOT NULL, `sender` TEXT NOT NULL, `content` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`heldAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_held_group_messages_groupId` ON `held_group_messages` (`groupId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `voice_keys` (`groupId` TEXT NOT NULL, `channelId` TEXT NOT NULL, " +
                        "`version` INTEGER NOT NULL, `room` TEXT NOT NULL, `key` TEXT NOT NULL, `epoch` INTEGER NOT NULL, " +
                        "`holders` TEXT, PRIMARY KEY(`groupId`, `channelId`))",
                )
            }
        }

        /** Opens the SQLCipher database with [passphrase]; the caller gets it from Android Keystore. */
        fun open(context: Context, passphrase: ByteArray): BleskDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BleskDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13,
                )
                .build()
        }
    }
}
