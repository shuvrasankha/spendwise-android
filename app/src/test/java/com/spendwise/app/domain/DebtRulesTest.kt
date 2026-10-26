package com.spendwise.app.domain

import com.spendwise.app.data.sharedDebtFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtRulesTest {
    private val fmt = { n: Double -> "₹" + toFixed2(n) }
    private val today = LocalDate.parse("2026-10-26")

    private fun debt(
        person: String, type: String, amount: Double, date: String = "", email: String = "", paid: List<Payment> = emptyList(),
        settled: Boolean = false, shared: Boolean = false, claimDate: String = "", claimAmount: Double? = null, id: String = person + date + amount,
    ) = Debt(id, amount, type, person, date, "", settled, "", paid, claimDate, claimAmount, email, shared)

    // tests/debts.test.mjs
    private val debts = listOf(
        debt("Bibhu", THEY_OWE, 1768.0, "2026-10-25", "bibhu@x.com", listOf(Payment(500.0, "2026-10-25"))),
        debt("Bibhusundar Khanra", THEY_OWE, 300.0, "2026-10-20", "Bibhu@x.com"),
        debt("bibhu", I_OWE, 100.0, "2026-10-21"),
        debt("Ravi", I_OWE, 900.0, "2026-10-20", "ravi@y.com", listOf(Payment(200.0, "2026-10-22")), shared = true),
        debt("Ravi", THEY_OWE, 250.0, "2026-10-23", "ravi@y.com"),
        debt("Asha", THEY_OWE, 50.0, "2026-10-01"),
        debt("Asha", THEY_OWE, 60.0, "2026-10-02", settled = true),
    )

    @Test fun byPerson_oneRowPerEmail_nettingBothWays() {
        val rows = debtsByPerson(debts)
        assertEquals(
            listOf(
                listOf("Bibhu", "bibhu@x.com", 1568.0, 100.0, 1468.0, 3),
                listOf("Ravi", "ravi@y.com", 250.0, 700.0, -450.0, 2),
                listOf("Asha", "", 50.0, 0.0, 50.0, 1),
            ),
            rows.map { listOf(it.name, it.email, it.owedToYou, it.youOwe, it.net, it.count) },
        )
    }

    @Test fun byPerson_aNameWithTwoEmailsStaysTwoPeople() {
        val rows = debtsByPerson(listOf(debt("Amit", THEY_OWE, 10.0, email = "a1@x.com"), debt("Amit", THEY_OWE, 20.0, email = "a2@x.com"), debt("Amit", THEY_OWE, 5.0)))
        assertEquals(3, rows.size)
    }

    @Test fun byPerson_filter() {
        val bibhu = debtsByPerson(debts).first()
        assertEquals(3, debts.count { isPersons(it, bibhu) })
        val asha = debtsByPerson(debts).last()
        assertEquals(2, debts.count { isPersons(it, asha) }) // the settled one too; the page filters open debts first
    }

    @Test fun byPerson_wording() {
        val (bibhu, ravi) = debtsByPerson(debts)
        assertEquals("Owes you ₹1468.00", bibhu.balance(fmt))
        assertEquals("You owe ₹450.00", ravi.balance(fmt))
        assertEquals("2 open · ravi@y.com · ₹250.00 to you, ₹700.00 from you", ravi.sub(fmt))
    }

    @Test fun summary() {
        val s = debtSummary(debts)
        assertEquals(1268.0 + 300.0 + 250.0 + 50.0, s.owedToYou, 1e-9)
        assertEquals(800.0, s.youOwe, 1e-9)
        assertEquals(s.owedToYou - s.youOwe, s.net, 1e-9)
        assertEquals(6, s.open)
        assertEquals(5, s.people) // names as typed: Bibhu, Bibhusundar Khanra, bibhu, Ravi, Asha
        assertEquals(4, s.owedCount)
        assertEquals(2, s.oweCount)
        assertEquals("in your favour", s.netWord)
        assertEquals("No open debts", debtSummary(emptyList()).sub)
        assertEquals("all square", debtSummary(emptyList()).netWord)
        assertEquals("1 open debt with 1 person", debtSummary(listOf(debt("A", I_OWE, 5.0))).sub)
        assertEquals("you owe overall", debtSummary(listOf(debt("A", I_OWE, 5.0))).netWord)
    }

    // tests/split.test.mjs › people
    @Test fun people() {
        val list = listOf(
            debt("Ravi", THEY_OWE, 1.0, "2026-10-01", "ravi@x.com"),
            debt("ravi", THEY_OWE, 1.0, "2026-10-20"),
            debt("Asha", THEY_OWE, 1.0, "2026-10-10"),
            debt("Ravi", THEY_OWE, 1.0, "2026-09-01", "ravi@x.com"),
            sharedDebtFrom("s1", mapOf("type" to THEY_OWE, "fromName" to "Meera", "fromEmail" to "meera@y.com", "date" to "2026-10-15")),
            debt("Ravi", THEY_OWE, 1.0, "2026-08-01", "ravi.k@z.com"),
        )
        assertEquals(
            listOf(KnownPerson("Meera", "meera@y.com"), KnownPerson("Asha", ""), KnownPerson("Ravi", "ravi@x.com"), KnownPerson("Ravi", "ravi.k@z.com")),
            peopleFrom(list),
        )
    }

    @Test fun addChecks_inWebOrder() {
        fun msg(amount: String, person: String = "A", date: String = "2026-10-01", email: String = "") =
            (checkDebt(amount, THEY_OWE, person, date, "", email, "me@y.com") as? DebtCheck.Invalid)?.message
        assertEquals("Enter a valid amount.", msg("", person = ""))
        assertEquals("Enter a valid amount.", msg("0"))
        assertEquals("Enter person name.", msg("5", person = "  "))
        assertEquals("Select a date.", msg("5", date = ""))
        assertEquals("\"bob\" doesn't look like an email address.", msg("5", email = " Bob "))
        assertEquals("That's your own email. Use the other person's.", msg("5", email = "ME@y.com"))
        val ok = checkDebt("12.5abc", I_OWE, "  Asha ", "2026-10-01", " lunch ", " Asha@X.com ", "me@y.com") as DebtCheck.Ok
        assertEquals(DebtInput(12.5, I_OWE, "Asha", "2026-10-01", "lunch", "asha@x.com"), ok.input)
    }

    // tests/split.test.mjs › part-payments and claims
    @Test fun payAndClaim() {
        val d = debt("Bibhu", THEY_OWE, 1768.0, paid = listOf(Payment(500.0, "2026-10-20")))
        assertEquals(1268.0, d.left(), 0.0)
        assertEquals(listOf("2026-10-01", "2026-10-20"), (payDebt(d.amount, d.paid, 10.0, "2026-10-01") as PayResult.Ok).paid.map { it.date })
        assertEquals(1268.0, sharedDebtFrom("x", mapOf("amount" to 1768L, "type" to THEY_OWE, "paid" to listOf(mapOf("amount" to 500L, "date" to "2026-10-20")))).left(), 0.0)
        val c = d.copy(claimDate = "2026-10-25", claimAmount = 300.0)
        assertEquals(Claim(300.0, "2026-10-25", false), c.claim())
        assertEquals(Claim(1268.0, "2026-10-25", true), c.copy(claimAmount = null).claim())
        assertEquals(1268.0, c.copy(claimAmount = 5000.0).claim()!!.amount, 0.0)
        assertNull(c.copy(settled = true).claim())
    }

    @Test fun payDialogWording() {
        val own = debt("Bibhu", THEY_OWE, 1768.0, paid = listOf(Payment(500.0, "2026-10-20")))
        val shared = own.copy(shared = true)
        assertEquals("", payHint(own, null, fmt))
        assertEquals("That's more than the ₹1268.00 left.", payHint(own, 1300.0, fmt))
        assertEquals("Pays it off: it moves to Settled.", payHint(own, 1268.0, fmt))
        assertEquals("That's all of it. Bibhu confirms it, then it counts.", payHint(shared, 1268.0, fmt))
        assertEquals("₹1000.00 will be left.", payHint(own, 268.0, fmt))
        assertEquals("Save and settle", payButton(own, 1268.0))
        assertEquals("Save", payButton(own, 1.0))
        assertEquals("Tell Bibhu", payButton(shared, 1268.0))
        assertEquals("Bibhu paid you", payTitle(own))
        assertEquals("Pick the date it was paid.", payError(own, 5.0, "", fmt))
        assertEquals("Enter the amount paid.", payError(own, null, "2026-10-25", fmt))
        assertEquals("That's more than the ₹1268.00 left.", payError(own, 2000.0, "2026-10-25", fmt))
        assertNull(payError(own, 5.0, "2026-10-25", fmt))
        assertEquals(1268.0, parsePayAmount("₹1,268")!!, 0.0)
        assertNull(parsePayAmount("abc"))
    }

    @Test fun rowWording() {
        val own = debt("Bibhu", THEY_OWE, 1768.0, "2026-10-25", "b@x.com", claimDate = "2026-10-25", claimAmount = 300.0)
        assertEquals("Bibhu says they paid you ₹300.00", claimText(own, fmt))
        assertEquals("Owes you · 25 Oct · Shared · Bibhu says they paid you ₹300.00", openMeta(own, today, fmt))
        val shared = own.copy(type = I_OWE, shared = true, claimAmount = null)
        assertEquals("You owe · 25 Oct · Added by them · You said you paid ₹1768.00, all of it · waiting for them", openMeta(shared, today, fmt))
        assertEquals("Paid you back · 26 Oct", settledMeta(own.copy(settled = true, settledDate = "2026-10-26"), today))
        assertEquals("You paid back · Added by them", settledMeta(shared.copy(settled = true), today))
    }

    @Test fun reopen_takesOffTheLastPayment() {
        val paidOff = debt("A", THEY_OWE, 100.0, settled = true, paid = listOf(Payment(40.0, "2026-10-01"), Payment(60.0, "2026-10-05")))
        assertEquals(listOf(Payment(40.0, "2026-10-01")), reopenPaid(paidOff))
        assertEquals("₹60.00 with A moves back to Open, and the payment of ₹60.00 on 5 Oct is taken off.", reopenMessage(paidOff, today, fmt))
        val settledOutright = debt("A", THEY_OWE, 100.0, settled = true)
        assertNull(reopenPaid(settledOutright))
        assertEquals("₹100.00 with A moves back to Open.", reopenMessage(settledOutright, today, fmt))
        assertTrue(emailError("", "me@y.com") == null)
    }
}
