package app.blesk.server.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Connection.update(sql: String, vararg params: Any?): Int = prepareStatement(sql).use { statement ->
    statement.bind(params)
    statement.executeUpdate()
}

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> = prepareStatement(sql).use { statement ->
    statement.bind(params)
    statement.executeQuery().use { rows ->
        buildList { while (rows.next()) add(map(rows)) }
    }
}

fun <T> Connection.queryOne(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    query(sql, *params, map = map).firstOrNull()

fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

private fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { index, value ->
        val position = index + 1
        when (value) {
            is Instant -> setObject(position, value.atOffset(ZoneOffset.UTC))
            else -> setObject(position, value)
        }
    }
}
