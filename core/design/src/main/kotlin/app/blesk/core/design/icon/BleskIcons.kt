package app.blesk.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The blesk icon set: 24dp grid, one 1.75 stroke, round caps and joins, no fills.
 * Drawn here as path data so every icon shares exactly the same line.
 */
object BleskIcons {
    const val STROKE = 1.75f

    val Back by icon("M15 5l-7 7 7 7")
    val ChevronRight by icon("M9.5 5.5l6.5 6.5-6.5 6.5")
    val Smile by icon("M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0zM8.5 14c.9 1.4 2.1 2 3.5 2s2.6-.6 3.5-2M9.5 9.5v1M14.5 9.5v1")
    val ArrowDown by icon("M12 5v14M6 13l6 6 6-6")
    val At by icon("M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM16 8v5.5a2.5 2.5 0 0 0 5 0V12a9 9 0 1 0-3.5 7.1")
    val Close by icon("M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Check by icon("M5 12.5l4.5 4.5L19 7.5")
    val DoubleCheck by icon("M1.5 12l5 5 10-11M11 16l1 1 10-11")
    val Clock by icon("M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0zM12 7.5V12l3 2")
    val Alert by icon("M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0zM12 7.5v5.5M12 16.2v.3")
    val Plus by icon("M12 5v14M5 12h14")
    val NewChat by icon(
        "M4 11.5C4 7.4 7.6 4.5 12 4.5s8 2.9 8 7-3.6 7-8 7c-1 0-1.9-.1-2.8-.4L5 19.5l1-3.4C4.7 14.9 4 13.3 4 11.5zM12 8.5v6M9 11.5h6",
    )
    val Search by icon("M17.5 11a6.5 6.5 0 1 1-13 0 6.5 6.5 0 0 1 13 0zM16 16l4 4")
    val Chats by icon(
        "M4 11.5C4 7.4 7.6 4.5 12 4.5s8 2.9 8 7-3.6 7-8 7c-1 0-1.9-.1-2.8-.4L5 19.5l1-3.4C4.7 14.9 4 13.3 4 11.5z",
    )
    val Settings by icon(
        "M4 7h9M17.5 7H20M4 17h2.5M11 17h9M15 9.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM9 19.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
    )
    val Appearance by icon("M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0zM12 4v16M12 8h5.5M12 12h8M12 16h5.5")
    val Server by icon(
        "M5 4.5h14a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z" +
            "M5 13.5h14a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1zM7.5 7.5h.01M7.5 16.5h.01",
    )
    val Info by icon("M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0zM12 11v5M12 8h.01")
    val Debug by icon(
        "M4.5 5h15a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM7.5 10l2.5 2-2.5 2M12.5 14.5h4",
    )
    val Copy by icon(
        "M9.5 8.5h8.5a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H9.5a1 1 0 0 1-1-1V9.5a1 1 0 0 1 1-1z" +
            "M15.5 8.5V5.5a1 1 0 0 0-1-1H6a1 1 0 0 0-1 1V15a1 1 0 0 0 1 1h2.5",
    )
    val Lock by icon(
        "M7.5 10.5V8a4.5 4.5 0 0 1 9 0v2.5M6.5 10.5h11a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1zM12 14v2.5",
    )
    val Shield by icon("M12 3.5l7 2.8v5.2c0 4.2-2.9 7.3-7 9-4.1-1.7-7-4.8-7-9V6.3l7-2.8zM9 12l2.2 2.2L15.5 10")
    val People by icon(
        "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3.5 19c.6-3 2.7-5 5.5-5s4.9 2 5.5 5M16 11a2.5 2.5 0 1 0 0-5M17.5 14.2c1.6.6 2.7 2.3 3 4.8",
    )
    val Channel by icon("M4.5 10v4a1 1 0 0 0 1 1H8l7 4V5L8 9H5.5a1 1 0 0 0-1 1zM18.5 9.5c.9.7 1.5 1.6 1.5 2.5s-.6 1.8-1.5 2.5")
    val Hash by icon("M10 4.5l-2 15M16 4.5l-2 15M5.5 9h14M4.5 15h14")
    val Voice by icon("M12 5v14M8 8.5v7M16 8.5v7M4 11v2M20 11v2")
    val Bot by icon(
        "M6.5 8.5h11a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM12 8.5V5.5M12 4.5h.01M9.5 13h.01M14.5 13h.01",
    )
    val Send by icon("M5 12h13M13 6.5l5.5 5.5-5.5 5.5")
    val Mic by icon("M12 14.5a3 3 0 0 0 3-3v-4a3 3 0 0 0-6 0v4a3 3 0 0 0 3 3zM6.5 11.5a5.5 5.5 0 0 0 11 0M12 17v3")
    val Edit by icon("M5 19l1-4L15.5 5.5a2.1 2.1 0 0 1 3 3L9 18l-4 1zM13.5 7.5l3 3")
    val Trash by icon("M5 7h14M10 7V5h4v2M7 7l1 12.5h8L17 7M10.5 11v5M13.5 11v5")
    val Image by icon(
        "M5.5 5h13a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4.5 16l4.5-4.5 4 4 2.5-2.5 4 4M15 9.5h.01",
    )
    val Download by icon("M12 4.5v10.5M7.5 10.5l4.5 4.5 4.5-4.5M5 19.5h14")
    val Upload by icon("M12 15V4.5M7.5 9l4.5-4.5L16.5 9M5 19.5h14")
    val Fingerprint by icon(
        "M7 18.5c1.2-1.8 1.8-3.9 1.8-6.5a3.2 3.2 0 0 1 6.4 0c0 1.2-.1 2.4-.3 3.5M12 12c0 3-.8 5.6-2.4 7.8" +
            "M17.6 17.6c.6-1.8.9-3.7.9-5.6a6.5 6.5 0 0 0-11.6-4M5.5 15.2c.3-1 .5-2.1.5-3.2 0-.9.2-1.8.5-2.6M14.5 19.5c.4-.7.8-1.5 1-2.3",
    )
    val Backspace by icon("M9 5.5h10a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5H9L3.5 12 9 5.5zM12 9.5l5 5M17 9.5l-5 5")
    val Qr by icon(
        "M4.5 4.5h5v5h-5zM14.5 4.5h5v5h-5zM4.5 14.5h5v5h-5zM14.5 14.5h2v2h-2zM17.5 17.5h2v2h-2zM14.5 19.5h.01M19.5 14.5h.01",
    )
    val Paperclip by icon("M16.5 8.5l-6.8 6.8a2 2 0 0 1-2.8-2.8l7.3-7.3a3.5 3.5 0 0 1 5 5l-7.6 7.6a5 5 0 0 1-7.1-7.1L11 4.2")
    val Play by icon("M8 5.5v13l10.5-6.5L8 5.5z")
    val Pause by icon("M8.5 5.5v13M15.5 5.5v13")
    val Document by icon("M7 3.5h6.5l4.5 4.5v12.5H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1zM13.5 3.5V8H18M9 12.5h6M9 16h6")
    val Camera by icon("M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val Stop by icon("M7.5 7.5h9v9h-9z")
    val Person by icon("M12 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5.5 19.5c.8-3.3 3.3-5.5 6.5-5.5s5.7 2.2 6.5 5.5")
    val Invite by icon("M10 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM3.5 19.5c.8-3.3 3.3-5.5 6.5-5.5 1.5 0 2.8.5 3.9 1.3M18 13v6M15 16h6")
    val Sticker by icon("M19.5 13V6.5a2 2 0 0 0-2-2h-11a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2H13zM19.5 13H15a2 2 0 0 0-2 2v4.5")
    val Phone by icon(
        "M6.5 4h2.8l1.4 3.8-2 1.6a10.5 10.5 0 0 0 5.9 5.9l1.6-2 3.8 1.4v2.8a1.8 1.8" +
            " 0 0 1-1.8 1.8A15.7 15.7 0 0 1 4.7 5.8 1.8 1.8 0 0 1 6.5 4z",
    )
    val PhoneEnd by icon("M3.2 14.2c4.9-4.3 12.7-4.3 17.6 0l-1.8 2.6-3.6-1.3-.3-2.6a9.8 9.8 0 0 0-6.2 0l-.3 2.6-3.6 1.3z")
    val MicOff by icon("M12 14.5a3 3 0 0 0 3-3v-4a3 3 0 0 0-6 0v4a3 3 0 0 0 3 3zM6.5 11.5a5.5 5.5 0 0 0 11 0M12 17v3M4.5 4.5l15 15")
    val Video by icon(
        "M4.5 7.5h9a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0" +
            " 1 3 15V9a1.5 1.5 0 0 1 1.5-1.5zM15 10.5l5.5-3v9l-5.5-3",
    )
    val VideoOff by icon(
        "M4.5 7.5h9a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 3" +
            " 15V9a1.5 1.5 0 0 1 1.5-1.5zM15 10.5l5.5-3v9l-5.5-3M3.5 4l16.5 16",
    )
    val Speaker by icon("M4.5 10v4a1 1 0 0 0 1 1H8l4.5 4V5L8 9H5.5a1 1 0 0 0-1 1zM16 9.2a3.8 3.8 0 0 1 0 5.6M18.6 6.6a7.5 7.5 0 0 1 0 10.8")
    val Screen by icon("M4.5 4.5h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1zM9 20h6M12 16.5V20")
    val Bell by icon("M6.5 16.5V11a5.5 5.5 0 0 1 11 0v5.5l1.5 2h-14l1.5-2zM10 20.5h4")
    val Storage by icon(
        "M5 6.5c0-1.4 3.1-2.5 7-2.5s7 1.1 7 2.5-3.1 2.5-7 2.5-7-1.1-7-2.5z" +
            "M5 6.5v11c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-11M5 12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5",
    )
    val Rooms by icon("M4.5 9.5a3 3 0 0 1 3-3h4a3 3 0 0 1 0 6h-4a3 3 0 0 1-3-3zM9.5 15.5a3 3 0 0 1 3-3h4a3 3 0 0 1 0 6h-4a3 3 0 0 1-3-3z")
    val Bookmark by icon("M7 4.5h10a1 1 0 0 1 1 1v14l-6-3.8-6 3.8v-14a1 1 0 0 1 1-1z")
    val BellOff by icon("M6.5 16.5V11a5.5 5.5 0 0 1 9-4.2M17.5 10.5v6l1.5 2H8M10 20.5h4M4.5 4.5l15 15")
    val Archive by icon("M4.5 5h15v4h-15zM5.5 9v9.5a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V9M10 12.5h4")
    val Pin by icon("M8.5 4.5h7M10 4.5v5.2L7 13.5h10l-3-3.8V4.5M12 13.5v6")
    val Timer by icon("M12 20a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM12 9.5V13l2.2 1.6M10 3.5h4")
    val More by icon("M6 12h.01M12 12h.01M18 12h.01")
    val Share by icon("M12 15V4.5M7.5 9L12 4.5 16.5 9M5 13.5v4A2.5 2.5 0 0 0 7.5 20h9a2.5 2.5 0 0 0 2.5-2.5v-4")
    val Forward by icon("M19 12l-6-6v3.5C8 9.5 5 12.5 5 18c1.6-3 4.3-4.5 8-4.5V17l6-5z")
    val Flip by icon("M5 10a7 7 0 0 1 12.6-3.6M19 14a7 7 0 0 1-12.6 3.6M17.8 3.5v3h-3M6.2 20.5v-3h3")

    private fun icon(pathData: String) = lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}
