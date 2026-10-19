package com.opencode.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

class MediaImportException(message: String) : Exception(message)

class AttachmentStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val root = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun import(
        conversationId: String,
        uriString: String,
        allowed: Set<MediaKind>,
    ): ChatAttachment = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val displayName = queryDisplayName(uri) ?: "Attachment"
        val mime = (resolver.getType(uri) ?: mimeFromName(displayName)).orEmpty().lowercase()
        val mediaKind = when {
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime in AUDIO_MIME_TYPES -> MediaKind.AUDIO
            else -> throw MediaImportException("Unsupported file type: ${mime.ifBlank { displayName }}")
        }
        if (mediaKind !in allowed) {
            throw MediaImportException("${mediaKind.label} attachments are not supported by this model.")
        }

        val directory = File(root, conversationId).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val extension = when (mediaKind) {
            MediaKind.IMAGE -> "jpg"
            MediaKind.AUDIO -> when (mime) {
                "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
                else -> "mp3"
            }
        }
        val target = File(directory, "$id.$extension")
        try {
            when (mediaKind) {
                MediaKind.IMAGE -> saveImage(uri, target)
                MediaKind.AUDIO -> copyLimited(uri, target, MAX_AUDIO_BYTES)
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        if (!target.exists() || target.length() == 0L) {
            target.delete()
            throw MediaImportException("Could not read the selected file.")
        }
        ChatAttachment(
            id = id,
            kind = mediaKind.name,
            fileName = displayName,
            mimeType = if (mediaKind == MediaKind.IMAGE) "image/jpeg" else mime,
            localPath = target.absolutePath,
            sizeBytes = target.length(),
        )
    }

    fun delete(attachment: ChatAttachment) {
        val file = File(attachment.localPath)
        file.delete()
        file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
    }

    private fun saveImage(uri: Uri, target: File) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDimension = max(info.size.width, info.size.height)
                if (maxDimension > MAX_IMAGE_DIMENSION) {
                    val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDimension
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeSampled(uri)
        }

        val scaled = if (max(bitmap.width, bitmap.height) > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }

        target.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }

    private fun decodeSampled(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw MediaImportException("Could not decode the selected image.")
    }

    private fun copyLimited(uri: Uri, target: File, limit: Long) {
        val input: InputStream = resolver.openInputStream(uri)
            ?: throw MediaImportException("Could not open the selected file.")
        input.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) {
                        throw MediaImportException(
                            "Audio files must be smaller than ${limit / (1024 * 1024)} MB.",
                        )
                    }
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    private fun mimeFromName(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        else -> null
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 1600
        private const val MAX_AUDIO_BYTES = 15L * 1024 * 1024
        private val AUDIO_MIME_TYPES = setOf(
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/mpeg",
            "audio/mp3",
        )
    }
}
