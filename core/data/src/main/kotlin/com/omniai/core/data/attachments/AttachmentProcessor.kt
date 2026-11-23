package com.omniai.core.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.omniai.core.common.Dispatcher
import com.omniai.core.common.Ids
import com.omniai.core.common.OmniDispatcher
import com.omniai.core.domain.chat.AttachmentImageLoader
import com.omniai.core.model.Attachment
import com.omniai.core.model.AttachmentType
import com.omniai.core.model.ImagePart
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AttachmentResult {
    data class Ok(val attachment: Attachment) : AttachmentResult
    data class Unsupported(val reason: String) : AttachmentResult
}

/**
 * Copies picked files into app-private storage, compresses images and extracts document text
 * locally. Extracted text is what gets sent to the model (the user can inspect it first);
 * whole binary documents are never uploaded.
 */
@Singleton
class AttachmentProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : AttachmentImageLoader {

    private val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun importFile(uri: Uri): AttachmentResult = withContext(io) {
        val (name, size) = queryNameAndSize(uri)
        val mime = resolver.getType(uri) ?: guessMime(name)
        if (size != null && size > MAX_INPUT_BYTES) return@withContext AttachmentResult.Unsupported("$name is larger than 25 MB.")
        val ext = name.substringAfterLast('.', "").lowercase()
        when {
            mime.startsWith("image/") -> importImage(uri, name)
            mime.startsWith("audio/") -> AttachmentResult.Unsupported("Audio files are not supported yet. Speech-to-text and audio input will need a provider that accepts audio.")
            mime.startsWith("video/") -> AttachmentResult.Unsupported("Video attachments are not supported in this version.")
            else -> importDocument(uri, name, mime, ext)
        }
    }

    private fun importImage(uri: Uri, name: String): AttachmentResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return AttachmentResult.Unsupported("Could not read this image.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return AttachmentResult.Unsupported("Could not decode this image.")
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val id = Ids.new()
        val out = File(dir, "$id.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        return AttachmentResult.Ok(Attachment(id, AttachmentType.IMAGE, name, "image/jpeg", out.absolutePath, out.length()))
    }

    private fun importDocument(uri: Uri, name: String, mime: String, ext: String): AttachmentResult {
        val id = Ids.new()
        val out = File(dir, "$id.${ext.ifEmpty { "bin" }}")
        resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: return AttachmentResult.Unsupported("Could not open $name.")
        val text = try {
            when {
                ext in TEXT_EXTENSIONS || mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> out.readText().take(MAX_TEXT_CHARS * 2)
                ext == "pdf" || mime == "application/pdf" -> extractPdf(out)
                ext == "docx" -> extractOfficeXml(out, { it == "word/document.xml" }, "w:t", paragraphTag = "w:p")
                ext == "pptx" -> extractOfficeXml(out, { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }, "a:t", paragraphTag = "a:p")
                ext == "xlsx" -> extractOfficeXml(out, { it == "xl/sharedStrings.xml" || (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")) }, "t", paragraphTag = "row")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            out.delete()
            return AttachmentResult.Unsupported("$name: this file type can't be read on the device. Supported: PDF, TXT, CSV, JSON, XML, Markdown, DOCX, XLSX, PPTX.")
        }
        val clean = text.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (clean.isBlank()) {
            out.delete()
            return AttachmentResult.Unsupported("$name contains no extractable text (it may be a scanned document).")
        }
        val truncated = if (clean.length > MAX_TEXT_CHARS) clean.take(MAX_TEXT_CHARS) + "\n\n[Truncated by OmniAI: ${clean.length - MAX_TEXT_CHARS} more characters not included]" else clean
        return AttachmentResult.Ok(Attachment(id, AttachmentType.DOCUMENT, name, mime, out.absolutePath, out.length(), truncated))
    }

    private fun extractPdf(file: File): String {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply { endPage = minOf(doc.numberOfPages, MAX_PDF_PAGES) }
            val text = stripper.getText(doc)
            return if (doc.numberOfPages > MAX_PDF_PAGES) "$text\n\n[Only the first $MAX_PDF_PAGES of ${doc.numberOfPages} pages were extracted]" else text
        }
    }

    /** Pulls text nodes out of Office Open XML parts without any external library. */
    private fun extractOfficeXml(file: File, partFilter: (String) -> Boolean, textTag: String, paragraphTag: String): String {
        val sb = StringBuilder()
        val textRegex = Regex("<$textTag(?:\\s[^>]*)?>([^<]*)</$textTag>")
        val paraEnd = "</$paragraphTag>"
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            val parts = sortedMapOf<String, String>(compareBy({ it.length }, { it }))
            while (entry != null) {
                if (!entry.isDirectory && partFilter(entry.name)) parts[entry.name] = readLimited(zip)
                entry = zip.nextEntry
            }
            parts.forEach { (part, xml) ->
                if (part.startsWith("ppt/slides/")) sb.append("\n--- ").append(part.substringAfterLast('/').removeSuffix(".xml")).append(" ---\n")
                xml.split(paraEnd).forEach { chunk ->
                    val line = textRegex.findAll(chunk).joinToString(if (paragraphTag == "row") "\t" else "") { decodeXml(it.groupValues[1]) }
                    if (line.isNotBlank()) sb.append(line).append('\n')
                }
            }
        }
        return sb.toString()
    }

    private fun readLimited(input: InputStream): String {
        val bytes = input.readBytes()
        return String(bytes, 0, minOf(bytes.size, 8_000_000), Charsets.UTF_8)
    }

    private fun decodeXml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    override suspend fun load(attachment: Attachment): ImagePart? = withContext(io) {
        val f = File(attachment.localPath)
        if (!f.exists() || attachment.type != AttachmentType.IMAGE) return@withContext null
        ImagePart(attachment.mimeType, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
    }

    /** Deletes the private copy of an attachment that was picked but never sent. */
    suspend fun discard(attachment: Attachment) = withContext(io) {
        val f = File(attachment.localPath)
        if (f.canonicalPath.startsWith(dir.canonicalPath)) f.delete()
        Unit
    }

    /** Deletes files no longer referenced by any message. */
    suspend fun cleanOrphans(referenced: Set<String>) = withContext(io) {
        val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
        dir.listFiles()?.filter { it.absolutePath !in referenced && it.lastModified() < cutoff }?.forEach { it.delete() }
        Unit
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment ?: "file"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it)?.let { n -> name = n } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        return name to size
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "csv" -> "text/csv"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_INPUT_BYTES = 25L * 1024 * 1024
        const val MAX_IMAGE_SIDE = 1568
        const val MAX_TEXT_CHARS = 100_000
        const val MAX_PDF_PAGES = 200
        val TEXT_EXTENSIONS = setOf("txt", "csv", "json", "xml", "md", "markdown", "log", "tsv", "yaml", "yml")
    }
}
