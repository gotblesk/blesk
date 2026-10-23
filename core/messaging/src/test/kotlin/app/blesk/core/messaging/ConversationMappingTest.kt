package app.blesk.core.messaging

import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMappingTest {
    private val row = ConversationEntity(
        id = "blesk",
        kind = ConversationKind.Channel.name,
        title = "blesk",
        protection = Protection.Open.name,
        peer = null,
        updatedAt = 0,
    )
    private val space = SpaceInfo("blesk", SpaceKind.Channel, "blesk", about = "", members = 3, channels = emptyList())

    @Test
    fun theServersOwnChannelIsMarkedOfficial() {
        assertTrue(row.toModel(space.copy(official = true)).official)
        assertFalse(row.toModel(space).official)
    }
}
