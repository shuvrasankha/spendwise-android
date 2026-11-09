package com.spendwise.app.data

import android.content.Context
import android.net.Uri
import com.spendwise.app.domain.CsvImportError
import com.spendwise.app.domain.STATEMENT_MAX_FILE_BYTES
import com.spendwise.app.domain.STATEMENT_TOO_BIG
import com.spendwise.app.domain.StatementKind
import com.spendwise.app.domain.statementKind
import com.spendwise.app.domain.xlsxToCsv
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The PDF needs a password: none was given, or [wrong] is true when the one given didn't open it. */
class StatementPasswordException(val wrong: Boolean) : Exception()

/** A picked statement (CSV, PDF or .xlsx) as text for the AI step. Read on the phone; nothing is kept. */
@Singleton
class StatementReader @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri, password: String?): Pair<StatementKind, String> = withContext(Dispatchers.IO) {
        val bytes = checkNotNull(context.contentResolver.openInputStream(uri)) { "Can't open $uri" }.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > STATEMENT_MAX_FILE_BYTES) throw CsvImportError(STATEMENT_TOO_BIG)
            }
            out.toByteArray()
        }
        val kind = statementKind(bytes.copyOf(minOf(8, bytes.size)))
        kind to when (kind) {
            StatementKind.CSV -> bytes.toString(Charsets.UTF_8)
            StatementKind.XLSX -> try {
                xlsxToCsv(bytes)
            } catch (e: CsvImportError) {
                throw e
            } catch (e: Exception) {
                throw CsvImportError("BAD_FILE") // not a workbook, or a damaged one
            }
            StatementKind.XLS -> throw CsvImportError("XLS")
            StatementKind.PDF -> pdfText(bytes, password)
        }
    }

    private fun pdfText(bytes: ByteArray, password: String?): String {
        PDFBoxResourceLoader.init(context)
        val doc = try {
            PDDocument.load(bytes, password.orEmpty())
        } catch (e: InvalidPasswordException) {
            throw StatementPasswordException(wrong = !password.isNullOrEmpty())
        } catch (e: IOException) {
            throw CsvImportError("BAD_FILE")
        }
        val text = doc.use { LayoutStripper().getText(it) }
        if (text.isBlank()) throw CsvImportError("PDF_NO_TEXT")
        return text
    }
}

/**
 * Text with each word placed by its position on the page, so a statement's columns stay under their headings:
 * without it an amount in "Withdrawal" and one in "Deposit" read the same.
 */
private class LayoutStripper : PDFTextStripper() {
    private val line = StringBuilder()

    init {
        sortByPosition = true
    }

    override fun writeString(text: String, positions: List<TextPosition>) {
        val col = ((positions.firstOrNull()?.xDirAdj ?: 0f) / POINTS_PER_CHAR).toInt()
        if (line.isNotEmpty() && line.length >= col) line.append(' ')
        while (line.length < col) line.append(' ')
        line.append(text)
    }

    override fun writeWordSeparator() {} // words are spaced by position instead

    override fun writeLineSeparator() = flush()
    override fun writeParagraphStart() { flush(); super.writeParagraphStart() }
    override fun writeParagraphEnd() { flush(); super.writeParagraphEnd() }
    override fun writePageEnd() { flush(); super.writePageEnd() }

    private fun flush() {
        if (line.isBlank()) return line.setLength(0)
        output.write(line.trimEnd().toString())
        output.write("\n")
        line.setLength(0)
    }

    private companion object {
        /** About one character of a statement's small type: an A4 page is ~120 columns. */
        const val POINTS_PER_CHAR = 5f
    }
}
