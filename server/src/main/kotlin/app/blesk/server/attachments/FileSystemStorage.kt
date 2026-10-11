package app.blesk.server.attachments

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileSystemStorage(private val directory: Path) : AttachmentStorage {
    init {
        Files.createDirectories(directory)
    }

    override fun put(id: String, content: InputStream): Long {
        val target = directory.resolve(id)
        // Written under a temporary name and moved into place, so a reader never sees half a file.
        val partial = Files.createTempFile(directory, "upload", ".part")
        try {
            val size = Files.newOutputStream(partial).use { content.copyTo(it) }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return size
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    override fun open(id: String): StoredObject? {
        val file = directory.resolve(id)
        if (!Files.isRegularFile(file)) return null
        return StoredObject(Files.size(file), Files.newInputStream(file))
    }

    override fun delete(id: String) {
        Files.deleteIfExists(directory.resolve(id))
    }
}
