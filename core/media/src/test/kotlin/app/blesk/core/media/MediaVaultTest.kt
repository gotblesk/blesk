package app.blesk.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MediaVaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vault = MediaVault(context)
    private val root = File(context.filesDir, "media")
    private val demoFolder = File(root, "demo")

    @After
    fun emptyTheVault() {
        root.deleteRecursively()
    }

    @Test
    fun inTheDemoNewFilesGoToTheDemoFolder() {
        vault.demo = true

        val recording = vault.newFile("ogg")

        assertEquals(demoFolder, recording.parentFile)
        assertTrue(demoFolder.isDirectory)
        assertTrue(vault.owns(recording.path))
    }

    @Test
    fun theLibraryStaysAtTheRootInTheDemo() {
        vault.demo = true

        assertEquals(root, vault.library().newFile("mp3").parentFile)
    }

    @Test
    fun clearingTheDemoLeavesTheRest() = runBlocking {
        val photo = vault.newFile("jpg").apply { writeText("account") }
        vault.demo = true
        val recording = vault.newFile("ogg").apply { writeText("demo") }
        val song = vault.library().newFile("mp3").apply { writeText("library") }

        vault.clearDemo()

        assertTrue(photo.exists())
        assertTrue(song.exists())
        assertFalse(recording.exists())
        assertFalse(demoFolder.exists())
    }

    @Test
    fun outsideTheDemoFilesGoToTheRoot() {
        vault.demo = true
        vault.demo = false

        assertEquals(root, vault.newFile("m4a").parentFile)
    }
}
