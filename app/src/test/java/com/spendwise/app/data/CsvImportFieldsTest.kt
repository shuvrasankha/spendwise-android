package com.spendwise.app.data

import com.spendwise.app.domain.CsvTx
import org.junit.Assert.assertEquals
import org.junit.Test

// The web's import docs, quirks included: income without encoding, expenses without tags.
class CsvImportFieldsTest {
    @Test fun incomeDoc() {
        val f = csvIncomeFields("u1", CsvTx(500.0, "Other", "Salary", "Net Banking", "2026-10-01", "", "income"), "TS")
        assertEquals(listOf("uid", "amount", "source", "date", "paymentType", "bank", "notes", "createdAt"), f.keys.toList())
        assertEquals(500L, f["amount"])
        assertEquals("Online", f["paymentType"])
    }

    @Test fun expenseDoc() {
        val f = csvExpenseFields("u1", CsvTx(12.5, "Groceries", "Milk", "Cash", "2026-10-01", "n", "expense"), "TS")
        assertEquals(listOf("uid", "amount", "category", "date", "payment", "cardName", "description", "notes", "encoding", "createdAt"), f.keys.toList())
        assertEquals(12.5, f["amount"])
    }
}
