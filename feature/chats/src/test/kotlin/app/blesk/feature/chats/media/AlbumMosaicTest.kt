package app.blesk.feature.chats.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumMosaicTest {

    @Test
    fun `every picture gets a place and no row holds more than three`() {
        (1..10).forEach { count ->
            val rows = rowsFor(count)
            assertEquals(count, rows.sum())
            assertTrue(rows.all { it in 1..3 })
        }
    }
}
