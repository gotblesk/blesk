package app.blesk

import android.content.Context
import app.blesk.core.crypto.DatabaseKey
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.messaging.AccountManager

/**
 * The encrypted database and the keys of server mode: one per process, whichever server they serve.
 * The database is one file, and [SignalStore.lock] only guards the ratchets if every writer shares it.
 */
internal class ServerVault(context: Context) {
    val database: BleskDatabase by lazy { BleskDatabase.open(context, DatabaseKey(context).obtain()) }
    val store: SignalStore by lazy { SignalStore(database.signal()) }
    val keys: KeyManager by lazy { KeyManager(store) }
    val accounts: AccountManager by lazy { AccountManager(store, keys) }
}
