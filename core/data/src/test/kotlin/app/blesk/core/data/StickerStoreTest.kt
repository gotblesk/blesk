package app.blesk.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.model.StickerFormat
import app.blesk.core.data.stickers.StickerSet
import app.blesk.core.data.stickers.StickerStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StickerStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clean() {
        File(context.filesDir, "stickers").deleteRecursively()
    }

    @Test
    fun aSetKeepsItsStickersAcrossRestarts() = runBlocking {
        val store = StickerStore(context)
        val set = store.createSet("  Коты  ")
        val sticker = store.add(set.id, picture(), "😺", "мяу", StickerFormat.Static)!!
        store.toggleFavorite(sticker.id)
        store.markUsed(sticker.id)

        val reopened = StickerStore(context).library.first { it.sets.isNotEmpty() }
        val saved = reopened.sets.single()
        assertEquals("Коты", saved.title)
        assertEquals("мяу", saved.stickers.single().caption)
        assertTrue(File(saved.stickers.single().path).exists())
        assertEquals(listOf(sticker.id), reopened.favorites)
        assertEquals(listOf(sticker.id), reopened.recent)
    }

    @Test
    fun emojiFindsStickersWhateverTheVariationSelector() = runBlocking {
        val store = StickerStore(context)
        val set = store.createSet("Сердца")
        store.add(set.id, picture(), "❤️", "", StickerFormat.Static)
        assertEquals(1, store.library.value.matching("❤").size)
        assertEquals(0, store.library.value.matching("😺").size)
    }

    @Test
    fun aFullSetTakesNoMore() = runBlocking {
        val store = StickerStore(context)
        val set = store.createSet("Много")
        repeat(StickerSet.MAX_STICKERS) { store.add(set.id, picture(), "🙂", "", StickerFormat.Static) }
        assertNull(store.add(set.id, picture(), "🙂", "", StickerFormat.Static))
    }

    @Test
    fun deletingASetForgetsItsFavourites() = runBlocking {
        val store = StickerStore(context)
        val set = store.createSet("На время")
        val sticker = store.add(set.id, picture(), "🙂", "", StickerFormat.Static)!!
        store.toggleFavorite(sticker.id)
        store.deleteSet(set.id)
        val library = store.library.value
        assertTrue(library.sets.isEmpty())
        assertTrue(library.favorites.isEmpty())
        assertFalse(File(context.filesDir, "stickers/${set.id}").exists())
    }

    private fun picture(): File = File.createTempFile("sticker", ".webp", context.cacheDir).apply { writeBytes(ByteArray(16)) }
}
