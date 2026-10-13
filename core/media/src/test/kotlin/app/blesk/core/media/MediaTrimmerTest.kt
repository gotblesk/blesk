package app.blesk.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTrimmerTest {
    private val pieces = listOf(Piece("a", 4_000), Piece("b", 3_000), Piece("c", 5_000))

    @Test
    fun wholeKeepsEveryPiece() {
        assertEquals(
            listOf(Clip("a", 0, 4_000), Clip("b", 0, 3_000), Clip("c", 0, 5_000)),
            MediaTrimmer.clips(pieces, 0, 12_000),
        )
    }

    @Test
    fun trimsTheEndsAcrossPieces() {
        assertEquals(
            listOf(Clip("a", 1_000, 4_000), Clip("b", 0, 3_000), Clip("c", 0, 2_000)),
            MediaTrimmer.clips(pieces, 1_000, 9_000),
        )
    }

    @Test
    fun leavesOutPiecesOutsideTheSelection() {
        assertEquals(listOf(Clip("b", 500, 2_500)), MediaTrimmer.clips(pieces, 4_500, 6_500))
    }
}
