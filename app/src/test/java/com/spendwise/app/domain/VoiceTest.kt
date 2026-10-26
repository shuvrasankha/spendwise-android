package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from tests/voice.test.mjs, plus the reply's JSON and the review checks from voice-command.js.
class VoiceTest {
    private val today = "2026-10-25"
    private fun clean(json: String) = cleanEntry(Json.parseToJsonElement(json), today)

    @Test fun `an expense keeps its card, notes, tags and monthly repeat`() {
        val e = clean("""{"type":"expense","amount":"3500","category":"Shopping","payment":"Credit Card","cardName":"HDFC Millennia",
            "description":"Phone EMI","notes":"No-cost","tags":["#gadgets","phone"],"repeat":true,"emiTotal":12}""")
        assertEquals(
            VoiceEntry(ENTRY_EXPENSE, 3500.0, today, "No-cost", category = "Shopping", payment = "Credit Card", cardName = "HDFC Millennia",
                description = "Phone EMI", tags = listOf("gadgets", "phone"), emiTotal = 12, emiRaw = "12", repeat = true),
            e,
        )
    }

    @Test fun `what the AI leaves out gets the Add form defaults`() {
        val e = clean("""{"type":"expense","amount":80,"category":"Rent","description":""}""")!!
        assertEquals("Home & Rent", e.category)
        assertEquals("UPI", e.payment)
        assertEquals("-", e.description)
        assertEquals(emptyList<String>(), e.tags)
        assertEquals(false, e.repeat)
        assertEquals(0, e.emiTotal)
    }

    @Test fun `a card name only goes with a card payment, an EMI count makes it repeat`() {
        assertEquals("", clean("""{"type":"expense","amount":5,"payment":"UPI","cardName":"SBI"}""")!!.cardName)
        assertEquals(true, clean("""{"type":"expense","amount":5,"emiTotal":6}""")!!.repeat)
        assertEquals(0, clean("""{"type":"expense","amount":5,"repeat":true,"emiTotal":1}""")!!.emiTotal)
        assertEquals(false, clean("""{"type":"expense","amount":5,"repeat":"true"}""")!!.repeat)
        assertEquals(true, clean("""{"type":"expense","amount":5,"repeat":"monthly"}""")!!.repeat)
    }

    @Test fun `income keeps its bank and notes`() {
        assertEquals(
            VoiceEntry(ENTRY_INCOME, 20000.0, "2026-10-20", "Invoice 42", source = "Freelance", paymentType = "Online", bank = "SBI"),
            clean("""{"type":"income","amount":20000,"source":"Freelance","paymentType":"Online","bank":"SBI","notes":"Invoice 42","date":"2026-10-20"}"""),
        )
    }

    @Test fun `a debt keeps a valid email, lowercased, to share it`() {
        assertEquals("ravi@gmail.com", clean("""{"type":"debt","amount":500,"person":"Ravi","email":"Ravi@Gmail.com"}""")!!.email)
        assertEquals("", clean("""{"type":"debt","amount":500,"person":"Ravi","email":"ravi at gmail"}""")!!.email)
        assertEquals(THEY_OWE, clean("""{"type":"debt","amount":500,"debtType":"owes"}""")!!.debtType)
    }

    @Test fun `nothing usable - no entry`() {
        assertNull(clean("""{"type":"expense","amount":0}"""))
        assertNull(clean("""{"type":"none"}"""))
        assertNull(clean("""{"type":"expense"}"""))
        assertNull(clean("[1]"))
    }

    @Test fun `tags and EMI counts`() {
        assertEquals(listOf("goa2026", "wedding"), parseTags("#goa2026, wedding  goa2026"))
        assertEquals(12, emiCount("12"))
        assertEquals(0, emiCount("1.5"))
        assertEquals(0, emiCount(""))
    }

    @Test fun `the reply's JSON - in a code block, an array, or one object`() {
        fun types(reply: String) = readEntries(reply).map { (it as JsonObject)["type"].toString() }
        assertEquals(listOf("\"expense\"", "\"income\""), types("Here:\n```json\n[{\"type\":\"expense\"},{\"type\":\"income\"}]\n```"))
        assertEquals(listOf("\"debt\""), types("Sure! {\"type\":\"debt\"} done"))
        assertEquals(0, readEntries("[]").size)
    }

    @Test(expected = Exception::class) fun `a reply that isn't JSON throws`() {
        readEntries("Sorry, I can't help with that.")
    }

    @Test fun `review problems name the entry when there are several`() {
        val ok = VoiceEntry(ENTRY_EXPENSE, 10.0, today, "")
        assertNull(reviewProblem(listOf(ok), "me@x.com"))
        assertEquals("Enter an amount above zero.", reviewProblem(listOf(ok.copy(amount = 0.0)), "me@x.com"))
        val debt = VoiceEntry(ENTRY_DEBT, 10.0, today, "", debtType = THEY_OWE)
        assertEquals("Entry 2: Add who the debt is with.", reviewProblem(listOf(ok, debt), "me@x.com"))
        assertEquals("Entry 1: Total payments should be a whole number from 2 to 600.",
            reviewProblem(listOf(ok.copy(repeat = true, emiRaw = "1"), debt), "me@x.com"))
        assertEquals(emailError("me@x.com", "me@x.com"), reviewProblem(listOf(debt.copy(person = "Ravi", email = "me@x.com")), "me@x.com"))
    }

    @Test fun `the saved document's input matches toDocData`() {
        val e = VoiceEntry(ENTRY_EXPENSE, 3500.0, today, "", category = "Shopping", payment = "UPI", cardName = "HDFC", description = "-",
            repeat = true, emiTotal = 12)
        val input = e.expenseInput()
        assertEquals("", input.cardName)
        assertEquals("monthly", input.repeat)
        assertEquals(12, input.emiTotal)
        assertNull(e.copy(repeat = false).expenseInput().repeat)
        assertNull(e.copy(emiTotal = 0).expenseInput().emiTotal)
    }

    @Test fun `amount box and save label`() {
        assertEquals(1234.5, voiceAmount("₹1,234.5"), 0.0)
        assertEquals(0.0, voiceAmount("abc"), 0.0)
        val e = VoiceEntry(ENTRY_EXPENSE, 10.0, today, "")
        assertEquals("Save · 10", voiceSaveLabel(listOf(e)) { jsNumberText(it) })
        assertEquals("Save 2 · 10", voiceSaveLabel(listOf(e, e.copy(amount = 0.0))) { jsNumberText(it) })
        assertEquals("2 entries saved · 20", voiceSaved(listOf(e, e)) { jsNumberText(it) })
    }

    @Test fun `a bank SMS becomes a review entry`() {
        val e = voiceEntryFrom(SmsEntry.Spent(250.0, today, "Food & Dining", "UPI", "", ""))
        assertEquals("-", e.description)
        assertEquals("250", e.amountText)
    }

    @Test fun `the prompt names the language and lists every category`() {
        val p = voicePrompt(java.time.LocalDate.parse(today), VOICE_LANGUAGES[1])
        assertTrue(p.contains("Today is Sunday, 2026-10-25 (October 25, 2026)."))
        assertTrue(p.contains("हिन्दी (hi-IN)"))
        EXPENSE_CATEGORIES.forEach { assertTrue(p.contains("- $it: ")) }
    }
}
