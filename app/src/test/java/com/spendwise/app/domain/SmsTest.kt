package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// Ported from tests/sms.test.mjs.
class SmsTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun read(text: String) = parseSms(text, today)
    private fun spent(text: String) = read(text) as SmsEntry.Spent
    private fun earned(text: String) = read(text) as SmsEntry.Earned

    @Test fun `UPI debit to a VPA`() {
        val e = spent("Rs.450.00 debited from A/c XX1234 on 21-10-26 to VPA swiggy@icici (UPI Ref No 629451234567). Not you? Call 18002586161 -HDFC Bank")
        assertEquals(SmsEntry.Spent(450.0, "2026-10-21", "Food & Dining", "UPI", "", "Swiggy"), e)
    }

    @Test fun `credit card spend, ignoring the available limit`() {
        val e = spent("INR 1,250.00 spent on ICICI Bank Credit Card XX4321 at AMAZON on 2026-10-20. Avl Lmt: INR 98,750.00. To dispute, call 18001080")
        assertEquals(SmsEntry.Spent(1250.0, "2026-10-20", "Shopping", "Credit Card", "ICICI", "Amazon"), e)
    }

    @Test fun `Sent To format with a slash date`() {
        val e = spent("Sent Rs.250.00 From HDFC Bank A/C x1234 To ZOMATO LTD On 19/10/26 Ref 629312345678 Not You? Call 18002586161")
        assertEquals(250.0, e.amount, 0.0)
        assertEquals("2026-10-19", e.date)
        assertEquals("Zomato Ltd", e.description)
        assertEquals("Food & Dining", e.category)
    }

    @Test fun `SBI debit with a month name and no merchant`() {
        val e = spent("Dear Customer, Your A/C XXXXX5678 has been debited by Rs. 2,000.00 on 18Oct26 for ATM withdrawal. Avl Bal Rs 12,345.67 -SBI")
        assertEquals(SmsEntry.Spent(2000.0, "2026-10-18", "Other", "Debit Card", "SBI", "Bank payment"), e)
    }

    @Test fun `salary credit is income, ignoring the balance`() {
        val i = earned("Your A/c XX1234 is credited with Rs 52,000.00 on 01-Oct-26 by NEFT from ACME CORP towards SALARY OCT. Avl Bal Rs 60,100.00 -Axis Bank")
        assertEquals(SmsEntry.Earned(52000.0, "2026-10-01", "Salary", "Online", "Axis"), i)
    }

    @Test fun `UPI money received from a person`() {
        val i = earned("You have received Rs.300.00 from ravi.kumar@okaxis in your Kotak Bank a/c XX9876 on 21/10/2026. UPI Ref 6291234")
        assertEquals(300.0, i.amount, 0.0)
        assertEquals("Ravi Kumar", i.source)
        assertEquals("Kotak", i.bank)
    }

    @Test fun `debited then credited to beneficiary is a debit`() {
        val e = spent("Rs 1,000 debited from a/c XX1234 and credited to a/c XX9999 (IMPS Ref 1234) on 20-10-26")
        assertEquals("Net Banking", e.payment)
    }

    @Test fun `refund is income with source Refund`() {
        assertEquals("Refund", earned("Refund of Rs 799.00 from Flipkart has been credited to your A/c XX1234 on 15-10-26.").source)
    }

    @Test fun `a future or impossible date falls back to today`() {
        assertEquals("2026-10-22", read("Rs 99 debited from A/c XX1 on 30-12-26 to VPA uber@axl")!!.date)
        assertEquals("2026-10-22", read("Rs 99 debited from A/c XX1 on 31-02-26 to VPA uber@axl")!!.date)
    }

    @Test fun `QR codes are not used as names`() {
        assertEquals("Bank payment", spent("Rs 60 paid to paytmqr2810050501011abcd@paytm via UPI from A/c XX12").description)
    }

    @Test fun `not a transaction`() {
        assertNull(read("123456 is your OTP for a transaction of Rs 500 at Amazon. Do not share it."))
        assertNull(read("Your credit card bill of Rs 5,000 is due on 25-10-26. Min amount due Rs 250."))
        assertNull(read("Ravi has requested Rs 200 from you on UPI. Pay now."))
        assertNull(read("Transaction of Rs 300 at Swiggy using card XX12 has failed."))
        assertNull(read("Spent 200 on groceries today"))
        assertNull(read(""))
    }

    @Test fun `subscriptions, rent and investments use the merged categories`() {
        assertEquals("Bills & Utilities", spent("Rs.649.00 debited from A/c XX1234 on 20-10-26 to VPA netflix@icici (UPI Ref No 629451234567) -HDFC Bank").category)
        assertEquals("Home & Rent", spent("Rs.18000.00 debited from A/c XX1234 on 01-10-26 to VPA nobroker@icici (UPI Ref No 629451234568) -HDFC Bank").category)
        assertEquals("Savings & Investments", spent("Rs.5000.00 debited from A/c XX1234 on 05-10-26 to VPA zerodha@icici (UPI Ref No 629451234569) -HDFC Bank").category)
    }

    @Test fun `SBI UPI debit with no currency before the amount`() {
        val e = spent("Dear UPI user A/C X1234 debited by 250.0 on date 21Oct26 trf to ZOMATO Refno 629812345678. If not u? call 1800111109. -SBI")
        assertEquals(SmsEntry.Spent(250.0, "2026-10-21", "Food & Dining", "UPI", "", "Zomato"), e)
    }

    @Test fun `Rs colon amount, ignoring the balance`() {
        val e = spent("A/c *1234 Debited for Rs:100.00 on 21-10-2026 10:20:30 by Mob Bk ref no 629812 Avl Bal Rs:5000.00.If not you, Call 1800-180-1111 -Canara Bank")
        assertEquals(100.0, e.amount, 0.0)
        assertEquals("2026-10-21", e.date)
    }

    @Test fun `ICICI payee credited names who was paid`() {
        val e = spent("ICICI Bank Acct XX123 debited for Rs 499.00 on 21-Oct-26; Netflix credited. UPI:629811112222. Call 18002662 for dispute.")
        assertEquals(499.0, e.amount, 0.0)
        assertEquals("Netflix", e.description)
        assertEquals("Bills & Utilities", e.category)
    }

    @Test fun `line breaks in a pasted SMS read as spaces`() {
        assertEquals(450.0, read("Rs.450.00 debited\nfrom A/c XX1234 to VPA swiggy@icici")!!.amount, 0.0)
    }
}
