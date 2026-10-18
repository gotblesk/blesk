package app.blesk.server.db

import app.blesk.server.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

class Database(private val dataSource: DataSource) {
    /** Runs [block] in one transaction on the IO dispatcher; any exception rolls it back. */
    suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (expected: Throwable) {
                connection.rollback()
                throw expected
            }
        }
    }

    /** Whether `SELECT 1` answers within [timeoutSeconds]; blocks the calling thread. */
    fun ping(timeoutSeconds: Int): Boolean = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.queryTimeout = timeoutSeconds
            statement.executeQuery("SELECT 1").use { it.next() }
        }
    }

    companion object {
        fun migrate(dataSource: DataSource) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        fun pool(config: DatabaseConfig): HikariDataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = POOL_SIZE
                poolName = "blesk"
            },
        )

        private const val POOL_SIZE = 10
    }
}
