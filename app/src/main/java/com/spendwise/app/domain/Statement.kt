package com.spendwise.app.domain

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

// Bank statements as CSV, PDF or Excel (.xlsx). Each becomes plain text lines for the same AI step as a CSV.

enum class StatementKind { CSV, PDF, XLSX, XLS }

/** By the file's first bytes, not its name: a PDF starts "%PDF", an .xlsx is a zip, an old .xls an OLE file. */
fun statementKind(head: ByteArray): StatementKind = when {
    head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "%PDF" -> StatementKind.PDF
    head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> StatementKind.XLSX
    head.size >= 4 && head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() && head[2] == 0x11.toByte() && head[3] == 0xE0.toByte() -> StatementKind.XLS
    else -> StatementKind.CSV
}

/** A PDF or Excel file is read up to this size; its text then has CSV's 512 KB limit. */
const val STATEMENT_MAX_FILE_BYTES = 10 * 1024 * 1024

/** A PDF's lines include headers and wrapped descriptions, so it may have more lines than a CSV's 200 rows. */
const val PDF_MAX_LINES = 400

private val HEADER_WORDS = Regex("""\b(debit|credit|withdrawals?|deposits?|amount|balance|dr|cr)\b""", RegexOption.IGNORE_CASE)

/**
 * The statement from its table's header row on: the bank's address, account number and summary above it are
 * neither sent to the AI nor taken as the header. Unchanged when no row has "date" and an amount word.
 */
fun fromHeaderRow(text: String): String {
    val lines = text.lines()
    val i = lines.take(60).indexOfFirst { it.contains("date", ignoreCase = true) && HEADER_WORDS.containsMatchIn(it) }
    return if (i <= 0) text else lines.drop(i).joinToString("\n")
}

private fun csvField(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

/** The workbook's first sheet as CSV; dates as YYYY-MM-DD, empty rows left out. */
fun xlsxToCsv(bytes: ByteArray): String {
    val parts = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.forEach { e -> if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) parts[e.name.removePrefix("/")] = zip.readBytes() }
    }
    fun doc(name: String) = parts[name]?.let { xml(it) }
    val shared = doc("xl/sharedStrings.xml")?.let { d -> d.all("si").map { si -> si.all("t").joinToString("") { it.textContent } } }.orEmpty()
    val dateStyles = doc("xl/styles.xml")?.let(::dateStyles).orEmpty()
    val sheet = firstSheet(doc("xl/workbook.xml"), doc("xl/_rels/workbook.xml.rels"))?.let { parts[it] }
        ?: parts.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()?.let { parts[it] }
        ?: throw CsvImportError("NO_DATA_EXTRACTED")

    return xml(sheet).all("row").mapNotNull { row ->
        val cells = sortedMapOf<Int, String>()
        row.all("c").forEachIndexed { n, c ->
            val col = c.getAttribute("r").takeWhile { it.isLetter() }.fold(0) { a, ch -> a * 26 + (ch.uppercaseChar() - 'A' + 1) }.takeIf { it > 0 }?.minus(1) ?: n
            val v = c.all("v").firstOrNull()?.textContent.orEmpty()
            cells[col] = when (c.getAttribute("t")) {
                "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                "inlineStr" -> c.all("t").joinToString("") { it.textContent }
                "str", "b", "e" -> v
                else -> v.toDoubleOrNull()?.let { d ->
                    if (c.getAttribute("s").toIntOrNull() in dateStyles && d > 0) excelDate(d) else BigDecimal(d).round(MathContext(15)).stripTrailingZeros().toPlainString()
                } ?: v
            }.trim()
        }
        if (cells.values.all { it.isEmpty() }) null else (0..cells.lastKey()).joinToString(",") { csvField(cells[it].orEmpty()) }
    }.joinToString("\n")
}

/** Excel's day number (1900 system; day 60 is its made-up 29 Feb 1900, so later days count from 30 Dec 1899). */
fun excelDate(serial: Double): String = LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString()

private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    isExpandEntityReferences = false
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement

private fun Element.all(tag: String): List<Element> = getElementsByTagNameNS("*", tag).let { l -> (0 until l.length).map { l.item(it) as Element } }

/** The workbook's first sheet's part, as "xl/worksheets/sheet1.xml". */
private fun firstSheet(workbook: Element?, rels: Element?): String? {
    val id = workbook?.all("sheet")?.firstOrNull()?.attributes?.let { a -> (0 until a.length).map { a.item(it) }.firstOrNull { it.localName == "id" }?.nodeValue } ?: return null
    val target = rels?.all("Relationship")?.firstOrNull { it.getAttribute("Id") == id }?.getAttribute("Target") ?: return null
    return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
}

/** Indexes of the cell styles whose number format shows a date. */
private fun dateStyles(styles: Element): Set<Int> {
    val custom = styles.all("numFmt").associate { it.getAttribute("numFmtId").toIntOrNull() to it.getAttribute("formatCode") }
    fun isDate(id: Int) = id in 14..22 || id in 27..36 || id in 45..47 || id in 50..58 ||
        custom[id]?.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "")?.contains(Regex("[dDyY]")) == true
    val xfs = styles.all("cellXfs").firstOrNull()?.all("xf").orEmpty()
    return xfs.indices.filter { isDate(xfs[it].getAttribute("numFmtId").toIntOrNull() ?: 0) }.toSet()
}
