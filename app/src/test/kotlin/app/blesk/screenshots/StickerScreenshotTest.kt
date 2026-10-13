package app.blesk.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.StickerFormat
import app.blesk.core.data.stickers.Sticker
import app.blesk.core.data.stickers.StickerLibrary
import app.blesk.core.data.stickers.StickerSet
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.MediaVault
import app.blesk.core.media.StickerMaker
import app.blesk.core.media.VoiceRecorder
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.ComposerPanel
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.chats.EmojiPanel
import app.blesk.feature.chats.StickerShelf
import app.blesk.feature.chats.media.RecordMode
import app.blesk.feature.chats.media.RecordPermissionSheet
import app.blesk.feature.chats.media.Recorders
import app.blesk.feature.chats.stickers.StickerMakerScreen
import app.blesk.feature.chats.stickers.StickerSetActions
import app.blesk.feature.chats.stickers.StickerSetScreen
import app.blesk.feature.chats.stickers.StickerSetsScreen
import app.blesk.feature.chats.stickers.StickerSource
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.TimeZone

/** Stickers: the panel, a sticker in the chat, the sets and the maker, in every theme. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class StickerScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-11T15:00:00Z")
    private val seed by lazy { DemoSeed.from(context.resources, now) }

    private lateinit var stickers: List<Sticker>
    private lateinit var picture: Bitmap

    @Before
    fun drawStickers() {
        picture = face(0xFF3D7BE8.toInt(), 2, square = false)
        stickers = listOf(0xFFE8A33D, 0xFF4C9F70, 0xFF3D7BE8, 0xFFD9534F, 0xFF8E6CC9, 0xFF2BB3B1).mapIndexed { index, color ->
            val file = File(context.cacheDir, "sticker$index.png")
            val sticker = StickerMaker.render(face(color.toInt(), index), Rect(0, 0, SIDE, SIDE), true)
            file.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Sticker(id = "s$index", file = file.name, emoji = EMOJI[index], format = StickerFormat.Static, path = file.path)
        }
        // Robolectric's native graphics set themselves up on the first decode; several image
        // loader threads doing it at once abort the process, so one decode here comes first.
        BitmapFactory.decodeFile(stickers.first().path)
    }
    private val set by lazy { StickerSet(id = "own", title = "Мои стикеры", stickers = stickers) }
    private val library by lazy { StickerLibrary(sets = listOf(set), favorites = listOf("s2"), recent = listOf("s0", "s3")) }

    @Test
    fun panel() = capture("40_sticker_panel") {
        Column(Modifier.fillMaxSize()) {
            EmojiPanel(
                recent = emptyList(),
                onPick = {},
                stickers = StickerShelf(library, {}, {}, {}),
                startWithStickers = true,
            )
        }
    }

    @Test
    fun inChat() = capture("41_sticker_in_chat") {
        val base = seed.messages.first { it.conversationId == DemoSeed.ANYA }
        val sticker = stickers[1]
        ConversationScreen(
            conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
            messages = persistentListOf(
                base.copy(id = "hi", authorId = DemoSeed.ANYA, text = "Смотри, что сделала"),
                base.copy(id = "st", authorId = DemoSeed.ANYA, text = "", attachment = sticker.attachment()),
                base.copy(id = "me", authorId = "me", text = "", attachment = stickers[4].attachment()),
            ),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
        )
    }

    @Test
    fun emojiPanelInChat() = capture("45_chat_emoji_panel") { chatWith(ComposerPanel.Emoji) }

    @Test
    fun attachPanelInChat() = capture("46_chat_attach_panel") { chatWith(ComposerPanel.Attach) }

    @Test
    fun recordPermission() = capture("47_record_permission") {
        Box(Modifier.fillMaxSize()) {
            chatWith(ComposerPanel.None)
            RecordPermissionSheet(RecordMode.Circle, onDismiss = {})
        }
    }

    @Composable
    private fun chatWith(panel: ComposerPanel) {
        ConversationScreen(
            conversation = seed.conversations.first { it.id == DemoSeed.ANYA },
            messages = seed.messages.filter { it.conversationId == DemoSeed.ANYA }.toImmutableList(),
            typing = false,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {}, {}),
            recorders = MediaVault(context).let { vault ->
                Recorders("anya", VoiceRecorder(context, vault), CircleRecorder(context, vault), onVoice = {}, onCircle = {})
            },
            stickers = StickerShelf(library, {}, {}, {}),
            initialPanel = panel,
        )
    }

    @Test
    fun sets() = capture("42_sticker_sets") {
        StickerSetsScreen(library = library, onOpen = {}, onCreate = {}, onBack = {})
    }

    @Test
    fun set() = capture("43_sticker_set") {
        StickerSetScreen(set = set, actions = StickerSetActions({}, {}, {}, { _, _, _ -> }, {}, {}, {}))
    }

    @Test
    fun maker() = capture("44_sticker_maker") {
        StickerMakerScreen(
            source = StickerSource.Picture(picture),
            sets = listOf(set),
            initialSetId = set.id,
            onDone = {},
            onBack = {},
        )
    }

    /** A plain drawn shape with eyes, so the outline has a contour to follow. */
    private fun face(color: Int, variant: Int, square: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(SIDE, if (square) SIDE else SIDE * 3 / 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = minOf(cx, cy) * 0.7f
        if (variant % 2 == 0) {
            canvas.drawCircle(cx, cy, radius, paint)
        } else {
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, 60f, 60f, paint)
        }
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx - radius / 3, cy - radius / 4, radius / 6, paint)
        canvas.drawCircle(cx + radius / 3, cy - radius / 4, radius / 6, paint)
        paint.color = android.graphics.Color.BLACK
        canvas.drawCircle(cx - radius / 3, cy - radius / 4, radius / 12, paint)
        canvas.drawCircle(cx + radius / 3, cy - radius / 4, radius / 12, paint)
        return bitmap
    }

    private fun Sticker.attachment() = Attachment.Sticker(path, emoji, format)

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    companion object {
        private const val SIDE = 512
        private val EMOJI = listOf("😀", "👍", "❤️", "😂", "🔥", "🎉")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }

        @JvmStatic
        @BeforeClass
        fun fixTimeZone() {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        }
    }
}
