package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** backup.js, with the cases from tests/backup.test.mjs. */
class BackupTest {

    private fun expense(n: Int, vararg extra: Pair<String, Any?>) =
        mapOf<String, Any?>("id" to "exp$n", "uid" to "me", "amount" to 10L, "category" to "Food & Dining", "date" to "2026-10-21") + extra

    private fun file(expenses: List<Map<String, Any?>>, income: List<Any?> = emptyList(), debts: List<Any?> = emptyList(), holdings: List<Any?>? = null): String {
        val docs = { l: List<Any?> -> l.map { r -> (r as Map<*, *>).let { (it["id"] as? String ?: "") to (it - "id").mapKeys { k -> k.key.toString() } } } }
        val json = backupJson("2026-10-26T10:00:00.000Z", docs(expenses), docs(income), docs(debts), docs(holdings.orEmpty()))
        return if (holdings == null) json.replace(Regex(""",\s*"holdings": \[\s*]"""), "") else json
    }

    private fun ok(text: String) = (restorePlan(text, "me") as RestorePlan.Ok).writes
    private fun refs(text: String) = ok(text).map { "${it.collection}/${it.id ?: "AUTO"}" }

    @Test fun bigBackup_writtenInBatchesOfAtMost500() {
        val writes = ok(file((0 until 1201).map { expense(it) }))
        assertEquals(listOf(500, 500, 201), writes.chunked(BACKUP_BATCH).map { it.size })
    }

    @Test fun exportedIds_reused_soRestoringTwiceWritesTheSameDocs() {
        val text = file(listOf(expense(1), expense(2)))
        assertEquals(listOf("expenses/exp1", "expenses/exp2"), refs(text))
        assertEquals(refs(text), refs(text))
    }

    @Test fun noUsableId_getsANewOne_otherAccountsSkipped() {
        val text = """{"version":"1.0","expenses":[
            {"uid":"me","amount":10,"category":"Food & Dining"},
            {"id":"bad/id","uid":"me","amount":10,"category":"Food & Dining"},
            {"id":"x","uid":"someone-else","amount":10,"category":"Food & Dining"}],"income":[],"debts":[]}"""
        assertEquals(listOf("expenses/AUTO", "expenses/AUTO"), refs(text))
    }

    @Test fun holdings_keepIds_onlyAllowedFields_olderBackupsHaveNone() {
        val fund = mapOf("id" to "h1", "uid" to "me", "name" to "Axis Bluechip", "kind" to "mf", "value" to 12000L, "valueDate" to "2026-10-01", "goalId" to "g1", "extra" to "x")
        val text = file(
            listOf(expense(1, "holdingId" to "h1")),
            holdings = listOf(fund, mapOf("id" to "h2", "uid" to "me", "name" to "Crypto", "kind" to "crypto"), mapOf("id" to "h3", "uid" to "someone-else", "name" to "FD", "kind" to "fd")),
        )
        val writes = ok(text)
        assertEquals(listOf("holdings/h1", "expenses/exp1"), writes.map { "${it.collection}/${it.id}" })
        assertEquals(mapOf("uid" to "me", "name" to "Axis Bluechip", "kind" to "mf", "value" to 12000L, "valueDate" to "2026-10-01", "goalId" to "g1"), writes[0].fields)
        assertEquals("h1", writes[1].fields["holdingId"])
        assertEquals(listOf("expenses/exp1"), refs(file(listOf(expense(1)), holdings = null)))
    }

    @Test fun holding_valueNeedsItsDate() {
        assertEquals(setOf("id", "uid", "name", "kind"), holdingRecord(mapOf("id" to "h", "uid" to "me", "name" to "FD", "kind" to "fd", "value" to 5L))!!.keys)
        assertNull(holdingRecord(mapOf("name" to "  ", "kind" to "fd")))
        assertNull(holdingRecord(mapOf("name" to "x".repeat(61), "kind" to "fd")))
    }

    @Test fun validateRecord_likeTheWeb() {
        val e = mapOf<String, Any?>("amount" to 10L, "category" to "Food & Dining")
        assertTrue(validRecord(e, RecordType.EXPENSE))
        assertFalse(validRecord(e + ("amount" to "10"), RecordType.EXPENSE)) // a numeric string isn't a number here
        assertFalse(validRecord(e + ("amount" to 0L), RecordType.EXPENSE))
        assertFalse(validRecord(e + ("date" to "21/10/2026"), RecordType.EXPENSE))
        assertTrue(validRecord(e + ("date" to ""), RecordType.EXPENSE)) // falsy: not checked
        assertFalse(validRecord(e + ("category" to "x".repeat(51)), RecordType.EXPENSE))
        assertFalse(validRecord(e - "category", RecordType.EXPENSE))
        assertFalse(validRecord(e + ("payment" to 5L), RecordType.EXPENSE))
        val i = mapOf<String, Any?>("amount" to 5.5, "source" to "Salary")
        assertTrue(validRecord(i + ("paymentType" to "Cash"), RecordType.INCOME))
        assertFalse(validRecord(i + ("paymentType" to "Card"), RecordType.INCOME))
        val d = mapOf<String, Any?>("amount" to 5L, "person" to "Asha")
        assertTrue(validRecord(d + ("type" to "i-owe"), RecordType.DEBT))
        assertFalse(validRecord(d + ("type" to "owe"), RecordType.DEBT))
        assertFalse(validRecord(null, RecordType.DEBT))
    }

    @Test fun badFiles() {
        assertEquals(RestorePlan.Invalid, restorePlan("""{"version":"1.0","expenses":[],"income":[]}""", "me"))
        assertEquals(RestorePlan.Invalid, restorePlan("[]", "me"))
        assertEquals(RestorePlan.Broken, restorePlan("not json", "me"))
        assertEquals(RestorePlan.Broken, restorePlan("null", "me"))
        assertEquals(RestorePlan.Broken, restorePlan("""{"version":"1.0","expenses":[null],"income":[],"debts":[]}""", "me"))
        assertEquals(RestorePlan.Broken, restorePlan("""{"version":"1.0","expenses":{},"income":[],"debts":[]}""", "me"))
    }

    @Test fun export_isTheWebShape_andRestoresTheSameFields() {
        val created = mapOf("seconds" to 1761472800L, "nanoseconds" to 5000L)
        val fields = mapOf<String, Any?>("uid" to "me", "amount" to 500.0, "half" to 12.5, "category" to "Food & Dining", "tags" to listOf("goa"), "createdAt" to created)
        val json = backupJson("2026-10-26T10:00:00.000Z", listOf("e1" to fields), emptyList(), emptyList(), emptyList())
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(listOf("version", "exportDate", "expenses", "income", "debts", "holdings"), root.keys.toList())
        assertTrue(json.contains("\"amount\": 500,")) // a whole number, as JSON.stringify writes it
        assertEquals("1.0", root["version"]!!.jsonPrimitive.content)
        val back = ok(json).single()
        assertEquals("e1", back.id)
        assertEquals(fields + ("amount" to 500L), back.fields)
    }
}
