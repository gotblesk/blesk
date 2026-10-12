package app.blesk.core.data.model

import android.content.res.Resources
import app.blesk.core.data.R

/** How an attachment is named in previews and for TalkBack. */
fun Resources.attachmentLabel(attachment: Attachment?): String = when (attachment) {
    null -> ""
    is Attachment.Photo -> getString(R.string.attachment_photo)
    is Attachment.Video -> getString(R.string.attachment_video)
    is Attachment.File -> getString(R.string.attachment_file, attachment.name)
    is Attachment.Voice -> getString(R.string.attachment_voice)
    is Attachment.Circle -> getString(R.string.attachment_circle)
    is Attachment.Theme -> getString(R.string.attachment_theme)
    is Attachment.Album -> getString(R.string.attachment_album, attachment.items.size)
    is Attachment.Music -> getString(
        R.string.attachment_music,
        listOf(attachment.artist, attachment.title).filter { it.isNotEmpty() }.joinToString(" — "),
    )
}
