package app.blesk.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        OutboxEntity::class,
        SignalStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BleskDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun contacts(): ContactDao
    abstract fun outbox(): OutboxDao
    abstract fun signal(): SignalStateDao

    companion object {
        private const val NAME = "blesk.db"

        /** Opens the SQLCipher database with [passphrase]; the caller gets it from Android Keystore. */
        fun open(context: Context, passphrase: ByteArray): BleskDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BleskDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
