package app.blesk.core.media

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.StreamingCipher
import app.blesk.core.data.StreamSource
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EncryptedStreamTest {

    @Test
    fun `a video plays from the middle while the rest is still arriving`() {
        val key = StreamingCipher.newKey()
        val plain = Random.nextBytes(3 * 1024 * 1024)
        val sealed = ByteArrayOutputStream().also { StreamingCipher.encrypt(key, plain.inputStream(), it) }.toByteArray()
        val file = File.createTempFile("growing", ".part").apply { writeBytes(sealed.copyOf(sealed.size / 3)) }
        // The rest lands a moment later, as a download would.
        val writer = thread {
            Thread.sleep(300)
            file.appendBytes(sealed.copyOfRange(sealed.size / 3, sealed.size))
        }
        val stream = EncryptedStream(StreamSource(file, key, sealed.size.toLong()))
        val from = 2_000_000L
        stream.open(DataSpec.Builder().setUri(Uri.fromFile(file)).setPosition(from).build())
        val read = ByteArray(plain.size - from.toInt())
        var offset = 0
        while (offset < read.size) {
            val count = stream.read(read, offset, read.size - offset)
            if (count < 0) break
            offset += count
        }
        stream.close()
        writer.join()
        assertArrayEquals(plain.copyOfRange(from.toInt(), plain.size), read)
    }
}
