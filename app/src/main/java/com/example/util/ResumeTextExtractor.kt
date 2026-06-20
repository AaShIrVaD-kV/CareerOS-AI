package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream

object ResumeTextExtractor {

    /**
     * Extracts text from a given document Uri.
     * Supports PDF, DOCX, and TXT files.
     */
    fun extractText(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri)
        val mimeType = contentResolver.getType(uri) ?: ""

        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return ""

        return try {
            when {
                mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) -> {
                    extractFromPdf(context, inputStream)
                }
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || 
                        fileName.endsWith(".docx", ignoreCase = true) -> {
                    extractFromDocx(inputStream)
                }
                else -> {
                    // Default fallback to text/plain
                    extractFromText(inputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error extracting text: ${e.localizedMessage}"
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun extractFromPdf(context: Context, inputStream: InputStream): String {
        // Initialize PDFBox resource loader for Android
        PDFBoxResourceLoader.init(context)
        return try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                stripper.getText(document)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to parse PDF document. Ensure the file is not corrupted or encrypted."
        }
    }

    private fun extractFromDocx(inputStream: InputStream): String {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        val sb = java.lang.StringBuilder()
        try {
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val reader = zipInputStream.bufferedReader()
                    val content = reader.readText()
                    // Extract text inside <w:t> tags
                    val matcher = java.util.regex.Pattern.compile("<w:t[^>]*>(.*?)</w:t>").matcher(content)
                    while (matcher.find()) {
                        sb.append(matcher.group(1)).append(" ")
                    }
                    break
                }
                entry = zipInputStream.nextEntry
            }
        } finally {
            zipInputStream.close()
        }

        // Clean HTML/XML entities
        return sb.toString()
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }

    private fun extractFromText(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "resume.txt"
    }
}
