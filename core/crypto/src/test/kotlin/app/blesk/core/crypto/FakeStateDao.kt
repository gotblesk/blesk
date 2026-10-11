package app.blesk.core.crypto

import app.blesk.core.data.db.SignalStateDao
import app.blesk.core.data.db.SignalStateEntity

class FakeStateDao : SignalStateDao {
    private val rows = LinkedHashMap<Pair<String, String>, SignalStateEntity>()

    override fun get(kind: String, name: String) = rows[kind to name]
    override fun all(kind: String) = rows.values.filter { it.kind == kind }
    override fun count(kind: String) = rows.keys.count { it.first == kind }
    override fun put(entity: SignalStateEntity) {
        rows[entity.kind to entity.name] = entity
    }
    override fun delete(kind: String, name: String) {
        rows.remove(kind to name)
    }
    override fun deleteByPrefix(kind: String, prefix: String) {
        rows.keys.removeAll { it.first == kind && it.second.startsWith(prefix) }
    }
}
