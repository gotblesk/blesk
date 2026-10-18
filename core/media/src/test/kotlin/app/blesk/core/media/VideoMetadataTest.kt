package app.blesk.core.media

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4OrientationData
import androidx.media3.container.Mp4TimestampData
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class VideoMetadataTest {
    @Test
    fun `a sent video keeps its rotation and nothing else`() {
        val rotation = Mp4OrientationData(90)
        val entries = mutableSetOf<Metadata.Entry>(
            Mp4LocationData(55.75f, 37.62f),
            MdtaMetadataEntry("com.apple.quicktime.location.ISO6709", "+55.75+037.62/".encodeToByteArray(), 1),
            MdtaMetadataEntry("com.apple.quicktime.model", "Phone".encodeToByteArray(), 1),
            Mp4TimestampData(1_000L, 2_000L),
            rotation,
        )
        OnlyRotation.updateMetadataEntries(entries)
        assertEquals(setOf<Metadata.Entry>(rotation), entries)
    }
}
