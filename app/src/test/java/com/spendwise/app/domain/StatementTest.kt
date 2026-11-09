package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StatementTest {
    private fun zip(vararg parts: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z -> parts.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun kind_byFirstBytes() {
        assertEquals(StatementKind.PDF, statementKind("%PDF-1.7".toByteArray()))
        assertEquals(StatementKind.XLSX, statementKind(byteArrayOf(0x50, 0x4B, 3, 4)))
        assertEquals(StatementKind.XLS, statementKind(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())))
        assertEquals(StatementKind.CSV, statementKind("Date,Amount".toByteArray()))
        assertEquals(StatementKind.CSV, statementKind(ByteArray(0)))
    }

    @Test fun header_dropsTheBanksPreamble() {
        val text = "HDFC BANK\nAccount No 1234\nDate,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n01/11/26,Swiggy,250,,9750"
        assertEquals("Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n01/11/26,Swiggy,250,,9750", fromHeaderRow(text))
        assertEquals("a,b\n1,2", fromHeaderRow("a,b\n1,2")) // no header found: unchanged
    }

    @Test fun xlsx_firstSheet_sharedStrings_dates_andGaps() {
        val ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
        val bytes = zip(
            "xl/workbook.xml" to """<workbook $ns xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Txns" sheetId="1" r:id="rId2"/><sheet name="Other" sheetId="2" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst $ns><si><t>Date</t></si><si><t>Narration</t></si><si><r><t>UPI, </t></r><r><t>Swiggy</t></r></si><si><t>Debit</t></si></sst>""",
            "xl/styles.xml" to """<styleSheet $ns><numFmts><numFmt numFmtId="164" formatCode="dd\-mmm\-yy"/><numFmt numFmtId="165" formatCode="&quot;Rs&quot;#,##0.00"/></numFmts><cellXfs><xf numFmtId="0"/><xf numFmtId="164"/><xf numFmtId="14"/><xf numFmtId="165"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<worksheet $ns><sheetData><row><c r="A1" t="inlineStr"><is><t>wrong sheet</t></is></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<worksheet $ns><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="s"><v>3</v></c></row>
                <row r="2"/>
                <row r="3"><c r="A3" s="1"><v>46327</v></c><c r="B3" t="s"><v>2</v></c><c r="D3" s="3"><v>250.10000000000002</v></c></row>
                <row r="4"><c r="A4" s="2"><v>46328</v></c><c r="C4" t="str"><v>x</v></c></row>
            </sheetData></worksheet>""",
        )
        assertEquals("Date,Narration,,Debit\n2026-11-01,\"UPI, Swiggy\",,250.1\n2026-11-02,,x", xlsxToCsv(bytes))
    }

    @Test fun pdfLines_haveTheirOwnLimit() {
        val text = (1..300).joinToString("\n") { "line $it" }
        checkCsvLines(text, PDF_MAX_LINES, "PDF_TOO_LONG")
        assertEquals("PDF_TOO_LONG", runCatching { checkCsvLines(text + "\n" + text, PDF_MAX_LINES, "PDF_TOO_LONG") }.exceptionOrNull()?.message)
    }
}
