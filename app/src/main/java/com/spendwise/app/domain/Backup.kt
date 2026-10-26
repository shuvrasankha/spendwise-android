package com.spendwise.app.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Ported from backup.js (spec §7.8): the file format, validateRecord, holdingRecord and the restore plan.
// Values are plain Kotlin: Map, List, String, Long, Double, Boolean, null. Timestamps arrive as
// {seconds, nanoseconds} maps, the way JSON.stringify writes a Firestore Timestamp.

const val BACKUP_BATCH = 500
private val DOC_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val HOLDING_KIND_IDS = listOf("mf", "stocks", "fd", "rd", "ppf", "nps", "epf", "gold", "other")

/** One collection's documents as (id, fields). */
typealias BackupDocs = List<Pair<String, Map<String, Any?>>>

@OptIn(ExperimentalSerializationApi::class)
private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** exportData: {version, exportDate, expenses, income, debts, holdings}, each record {id, ...fields}. */
fun backupJson(exportDate: String, expenses: BackupDocs, income: BackupDocs, debts: BackupDocs, holdings: BackupDocs): String {
    // { id: d.id, ...d.data() }: a field named "id" wins over the doc id, as the spread does
    fun records(docs: BackupDocs) = docs.map { (id, fields) -> LinkedHashMap<String, Any?>().apply { put("id", id); putAll(fields) } }
    val data = linkedMapOf(
        "version" to "1.0", "exportDate" to exportDate,
        "expenses" to records(expenses), "income" to records(income), "debts" to records(debts), "holdings" to records(holdings),
    )
    return pretty.encodeToString(JsonElement.serializer(), toJson(data))
}

fun backupItemCount(expenses: BackupDocs, income: BackupDocs, debts: BackupDocs, holdings: BackupDocs) =
    expenses.size + income.size + debts.size + holdings.size

private fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Long, is Int -> JsonPrimitive((v as Number).toLong())
    is Double -> jsNumber(v)?.let(::JsonPrimitive) ?: JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
    is List<*> -> JsonArray(v.map(::toJson))
    else -> JsonPrimitive(v.toString())
}

/** A whole double within JS's safe range is an integer to JS: JSON.stringify writes 500, and the JS SDK writes a Long. */
private fun jsNumber(d: Double): Long? = if (d % 1.0 == 0.0 && kotlin.math.abs(d) <= 9007199254740991.0 && !(d == 0.0 && 1 / d < 0)) d.toLong() else null

private fun fromJson(e: JsonElement): Any? = when (e) {
    JsonNull -> null
    is JsonObject -> e.mapValues { fromJson(it.value) }
    is JsonArray -> e.map(::fromJson)
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        e.longOrNull != null -> e.longOrNull
        else -> e.doubleOrNull?.let { jsNumber(it) ?: it }
    }
}

/** JS truthiness, which validateRecord's `if (record.x && …)` checks rely on. */
private fun truthy(v: Any?): Boolean = when (v) {
    null -> false
    is Boolean -> v
    is String -> v.isNotEmpty()
    is Long -> v != 0L
    is Double -> v != 0.0 && !v.isNaN()
    else -> true
}

private fun tooLong(v: Any?, max: Int) = truthy(v) && (v !is String || v.length > max)

enum class RecordType { EXPENSE, INCOME, DEBT }

/** backup.js › validateRecord. */
fun validRecord(record: Any?, type: RecordType): Boolean {
    if (record !is Map<*, *>) return false
    val amount = record["amount"]
    if (amount !is Long && amount !is Double) return false
    if ((amount as Number).toDouble() <= 0) return false
    val date = record["date"]
    if (truthy(date) && !(date is String && ISO_DATE.matches(date))) return false
    if (truthy(record["notes"]) && record["notes"] !is String) return false
    if (tooLong(record["description"], 500) || tooLong(record["category"], 50)) return false
    if (tooLong(record["person"], 100) || tooLong(record["source"], 100)) return false
    return when (type) {
        RecordType.EXPENSE -> truthy(record["category"]) && !(truthy(record["payment"]) && record["payment"] !is String)
        RecordType.INCOME -> truthy(record["source"]) && !(truthy(record["paymentType"]) && record["paymentType"] !in listOf("Online", "Cash"))
        RecordType.DEBT -> truthy(record["person"]) && !(truthy(record["type"]) && record["type"] !in listOf("they-owe", "i-owe"))
    }
}

/** backup.js › holdingRecord: a holding as firestore.rules accepts it, only those fields; null to skip it. */
fun holdingRecord(r: Any?): Map<String, Any?>? {
    if (r !is Map<*, *>) return null
    val name = r["name"] as? String ?: return null
    if (name.isBlank() || name.length > 60) return null
    if (r["kind"] !in HOLDING_KIND_IDS) return null
    val value = r["value"]
    val valueDate = r["valueDate"]
    val valued = (value is Long || value is Double) && (value as Number).toDouble() >= 0 && valueDate is String && ISO_DATE.matches(valueDate)
    val goalId = r["goalId"]
    return buildMap {
        put("id", r["id"]); put("uid", r["uid"]); put("name", name); put("kind", r["kind"])
        if (valued) { put("value", value); put("valueDate", valueDate) }
        if (goalId is String && goalId.isNotEmpty() && goalId.length <= 40) put("goalId", goalId)
    }
}

/** One document to restore. [id] null: the record had no usable id, so it gets a new one. */
data class RestoreWrite(val collection: String, val id: String?, val fields: Map<String, Any?>)

sealed interface RestorePlan {
    data class Ok(val writes: List<RestoreWrite>) : RestorePlan
    /** Not a SpendWise backup: version, expenses, income or debts missing. */
    data object Invalid : RestorePlan
    /** The web's import throws here (bad JSON, a null record): "Import failed." */
    data object Broken : RestorePlan
}

/** importData: holdings first (keeping ids, so restored investment entries still point at them), then the rest. */
fun restorePlan(text: String, uid: String): RestorePlan {
    val data = runCatching { fromJson(Json.parseToJsonElement(text)) }.getOrElse { return RestorePlan.Broken }
    if (data == null) return RestorePlan.Broken // JSON.parse("null").version throws
    if (data !is Map<*, *>) return RestorePlan.Invalid
    if (listOf("version", "expenses", "income", "debts").any { !truthy(data[it]) }) return RestorePlan.Invalid

    val writes = mutableListOf<RestoreWrite>()
    fun add(collection: String, record: Map<*, *>) {
        val id = (record["id"] as? String)?.takeIf { DOC_ID.matches(it) }
        @Suppress("UNCHECKED_CAST")
        writes += RestoreWrite(collection, id, (record as Map<String, Any?>) - "id")
    }
    (data["holdings"] as? List<*>).orEmpty().forEach { r ->
        if ((r as? Map<*, *>)?.get("uid") == uid) holdingRecord(r)?.let { add("holdings", it) }
    }
    for ((type, collection) in listOf(RecordType.EXPENSE to "expenses", RecordType.INCOME to "income", RecordType.DEBT to "debts")) {
        // web quirk: for…of over a string walks its letters (none match); over an object or number it throws
        val records = when (val v = data[collection]) {
            is List<*> -> v
            is String -> emptyList<Any?>()
            else -> return RestorePlan.Broken
        }
        for (r in records) {
            if (r == null) return RestorePlan.Broken // record.uid throws on null
            if (r !is Map<*, *> || r["uid"] != uid || !validRecord(r, type)) continue
            add(collection, r)
        }
    }
    return RestorePlan.Ok(writes)
}

fun backupFileName(today: String) = "spendwise-backup-$today.json"
