package app.blesk.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {

    @Test
    fun `bars take the peak of each slice`() {
        val bars = Waveform.bars(listOf(0, 32767, 0, 0), count = 2)
        assertEquals(listOf(1f, 0f), bars)
    }

    @Test
    fun `short recordings still give the requested number of bars`() {
        assertEquals(48, Waveform.bars(listOf(1000, 2000)).size)
        assertEquals(48, Waveform.bars(emptyList()).size)
    }

    @Test
    fun `encoding survives a round trip within one step`() {
        val bars = listOf(0f, 0.5f, 1f)
        Waveform.decode(Waveform.encode(bars)).zip(bars).forEach { (a, b) -> assertEquals(b, a, 0.04f) }
    }
}
