package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// csv-upload.js: the line check, the reply's rows and the done message.
class CsvImportTest {
    private val today = "2026-10-26"

    @Test fun `lines - a header and a row, at most 200`() {
        assertEquals("CSV_TOO_SHORT", runCatching { checkCsvLines("date,amount\n\n") }.exceptionOrNull()?.message)
        assertEquals("CSV_TOO_MANY_ROWS", runCatching { checkCsvLines((1..201).joinToString("\n") { "r$it" }) }.exceptionOrNull()?.message)
        checkCsvLines("date,amount\n2026-10-01,5")
    }

    @Test fun `rows are checked and filled in`() {
        val rows = readCsvReply(
            """```json
[{"amount":"-250","category":"Rent","description":"","payment":"Paytm","date":"01/10/2026","notes":null,"type":"expense"},
 {"amount":50000,"category":"x","description":"Salary","payment":"Cash","date":"2026-10-01","notes":"Oct","type":"income"},
 {"amount":0,"type":"expense"}, {"amount":"abc"}]
```""", today,
        )
        assertEquals(
            listOf(
                CsvTx(250.0, "Home & Rent", "-", "UPI", today, "", "expense"),
                CsvTx(50000.0, "Other", "Salary", "Cash", "2026-10-01", "Oct", "income"),
            ),
            rows,
        )
    }

    @Test fun `a reply that isn't an array, or is empty`() {
        assertEquals("INVALID_FORMAT", runCatching { readCsvReply("""{"amount":5}""", today) }.exceptionOrNull()?.message)
        assertEquals("EMPTY_RESPONSE", runCatching { readCsvReply("", today) }.exceptionOrNull()?.message)
        assertTrue(readCsvReply("[]", today).isEmpty())
    }

    @Test fun `messages`() {
        assertEquals("Successfully imported 3 expenses and 1 income entry!", csvDoneText(3, 1))
        assertEquals("Successfully imported 2 income entries!", csvDoneText(0, 2))
        assertEquals("Error: File is too large (max 512 KB).", csvErrorText(CSV_TOO_BIG))
        // JSON.stringify of the lists: no spaces
        assertTrue(csvPrompt("a,b").first.contains(""""payment": one of ["UPI","Cash","Credit Card","Debit Card","Net Banking","Other"]"""))
        assertTrue(csvPrompt("a,b").second.endsWith("format:\n\na,b"))
    }

    @Test fun `chunks - each piece has the header, blank lines dropped, order kept`() {
        val text = "date,amount\n" + (1..120).joinToString("\n") { "d$it,$it" } + "\n\n"
        val pieces = csvChunks(text)
        assertEquals(listOf(51, 51, 21), pieces.map { it.lines().size })
        assertTrue(pieces.all { it.startsWith("date,amount\n") })
        assertEquals("d120,120", pieces.last().lines().last())
    }

    @Test fun `chunks - a quoted field over two lines stays one record`() {
        val pieces = csvChunks("a,b\n\"x\ny\",1\nz,2", rows = 1)
        assertEquals(listOf("a,b\n\"x\ny\",1", "a,b\nz,2"), pieces)
    }
}
