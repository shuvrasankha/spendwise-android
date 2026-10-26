package com.spendwise.app.ui.income

import com.spendwise.app.domain.CASH
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.ONLINE
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeFormTest {

    @Test fun cashClearsTheBank() {
        val f = IncomeForm(bank = "SBI").withPaymentType(CASH)
        assertEquals("", f.bank)
        assertEquals("", f.withPaymentType(ONLINE).bank)
    }

    @Test fun sourceChip_fillsTheLastPaymentTypeAndBank() {
        val last = Income("i", 1.0, "2026-10-01", "Acme", ONLINE, "HDFC", "")
        assertEquals(IncomeForm(source = "Acme", bank = "HDFC", paymentType = ONLINE), IncomeForm(paymentType = CASH).withSourceChip("Acme", last))
        val cash = last.copy(paymentType = CASH, bank = "")
        assertEquals(IncomeForm(source = "Acme", paymentType = CASH), IncomeForm(bank = "SBI").withSourceChip("Acme", cash))
        // An unknown source only fills the field
        assertEquals(IncomeForm(source = "New", bank = "SBI"), IncomeForm(bank = "SBI").withSourceChip("New", null))
    }

    @Test fun amountField_andEditValues() {
        assertEquals("12.5", IncomeForm().withAmount("12.5").amount)
        assertEquals("12.5", IncomeForm(amount = "12.5").withAmount("12.5.").amount)
        assertEquals("85000", IncomeForm.of(Income("i", 85000.0, "2026-10-01", "Salary", "", "", "")).amount)
        assertEquals(ONLINE, IncomeForm.of(Income("i", 182.5, "2026-10-01", "Salary", "", "", "")).paymentType)
        assertEquals("182.5", IncomeForm.of(Income("i", 182.5, "2026-10-01", "Salary", CASH, "", "")).amount)
    }
}
