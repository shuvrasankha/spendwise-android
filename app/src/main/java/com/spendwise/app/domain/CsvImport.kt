package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// CSV import (spec §7.8), ported from csv-upload.js: the AI maps the user's own CSV into rows, which are
// reviewed, then saved. Web quirks kept: imported income has no `encoding`, imported expenses no `tags`.

const val CSV_AI_MODEL = "@cf/qwen/qwen2.5-coder-32b-instruct"
const val CSV_MAX_BYTES = 512 * 1024
const val CSV_MAX_LINES = 200
const val CSV_BATCH = 500

/** Rows per AI call: ~45 reply tokens a row keeps each reply under the Worker's 4096-token cap. */
const val CSV_CHUNK_ROWS = 50

/** The AI writes about a row a second, so a 50-row piece takes about a minute: the Worker client's default 30 s cuts it off. */
const val CSV_READ_TIMEOUT_MS = 120_000

data class CsvTx(
    val amount: Double,
    val category: String,
    val description: String,
    val payment: String,
    val date: String,
    val notes: String,
    /** "expense" or "income". */
    val type: String,
)

/** The web's error codes, shown with their messages (handleFileUpload). */
class CsvImportError(val code: String) : Exception(code)

fun csvErrorText(code: String): String = when (code) {
    "RATE_LIMITED" -> "Rate limited by AI Provider. Please wait and try again."
    "MODEL_LOADING" -> "AI model is loading. Please try again in 20-30 seconds."
    "CSV_TOO_SHORT" -> "CSV file is too short. Need at least 2 rows (header + data)."
    "CSV_TOO_MANY_ROWS" -> "CSV is too large. Please limit to 200 rows at a time."
    "NO_DATA_EXTRACTED" -> "Could not extract any valid transactions from this CSV."
    "EMPTY_RESPONSE" -> "AI returned an empty response. Please try again."
    "INVALID_FORMAT" -> "AI returned data in unexpected format. Please try again."
    else -> "Error: $code"
}

const val CSV_TOO_BIG = "File is too large (max 512 KB)."
const val CSV_SAVE_FAILED = "Failed to save transactions. Please try again."

/** handleFileUpload's line check: at least a header and a row, at most 200 non-blank lines. */
fun checkCsvLines(text: String) {
    val lines = text.split('\n').count { it.isNotBlank() }
    if (lines < 2) throw CsvImportError("CSV_TOO_SHORT")
    if (lines > CSV_MAX_LINES) throw CsvImportError("CSV_TOO_MANY_ROWS")
}

/**
 * The CSV as pieces of at most [rows] records, each led by the header, so every AI reply fits (Android only;
 * the web sends the whole file and a long one comes back cut off). A quoted field may span lines.
 */
fun csvChunks(text: String, rows: Int = CSV_CHUNK_ROWS): List<String> {
    val records = mutableListOf<String>()
    var open: String? = null
    for (line in text.split('\n')) {
        val record = open?.let { it + "\n" + line } ?: line
        // An odd number of quotes so far: the record goes on to the next line
        open = if (record.count { it == '"' } % 2 == 1) record else null
        if (open == null && record.isNotBlank()) records += record
    }
    open?.takeIf { it.isNotBlank() }?.let { records += it }
    if (records.size < 2) return listOf(text)
    val header = records.first()
    return records.drop(1).chunked(rows) { (listOf(header) + it).joinToString("\n") }
}

private fun jsonList(v: List<String>) = v.joinToString(",", "[", "]") { "\"" + it.replace("\"", "\\\"") + "\"" }

/** buildCSVPrompt: (system, user). */
fun csvPrompt(csvText: String): Pair<String, String> {
    val system = """You are a data extraction assistant for SpendWise, an Indian expense tracker app.
The user will provide raw CSV data from their previous expense/income records.
Your job is to parse each row and map it into the SpendWise JSON format.

RULES:
1. Return ONLY a valid JSON array. No markdown, no explanation, no code fences.
2. Each object must have these exact keys:
   - "amount": number (positive, in Indian Rupees)
   - "category": one of ${jsonList(EXPENSE_CATEGORIES)}
   - "description": string (short description of the transaction)
   - "payment": one of ${jsonList(PAYMENT_METHODS)}
   - "date": string in "YYYY-MM-DD" format
   - "notes": string (any extra info, or empty string)
   - "type": "expense" or "income"
3. If a column doesn't clearly map, use your best judgement.
4. If the category doesn't match any valid option, use "Other".
5. If payment method is unclear, default to "UPI".
6. If a date is in DD/MM/YYYY or MM/DD/YYYY or other format, convert to YYYY-MM-DD.
7. If you cannot determine "type", assume "expense".
8. Skip header rows if present. Skip any obviously empty/invalid rows.
9. Amounts should always be positive numbers (no currency symbols).
10. If the CSV appears to have income entries (salary, freelance, refund, etc.), set type to "income".

Example output:
[
  {"amount": 250, "category": "Food & Dining", "description": "Lunch at restaurant", "payment": "UPI", "date": "2025-03-15", "notes": "", "type": "expense"},
  {"amount": 50000, "category": "Other", "description": "March Salary", "payment": "Net Banking", "date": "2025-03-01", "notes": "Monthly salary", "type": "income"}
]"""
    return system to "Here is my CSV data. Parse every valid row into the SpendWise JSON format:\n\n$csvText"
}

/** JS truthiness of a JSON value, for `entry.x || default`. */
private fun truthy(v: JsonElement?): Boolean = when (v) {
    null, JsonNull -> false
    is JsonPrimitive -> if (v.isString) v.content.isNotEmpty() else v.content != "false" && v.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } != false
    else -> true
}

/** String(v): what JS shows for a JSON value. */
private fun jsText(v: JsonElement): String = when (v) {
    JsonNull -> "null"
    is JsonPrimitive -> if (v.isString) v.content else v.content.toDoubleOrNull()?.let(::jsNumberText) ?: v.content
    is JsonArray -> v.joinToString(",") { if (it == JsonNull) "" else jsText(it) }
    is JsonObject -> "[object Object]"
}

/** callAIModel: the reply's array, each row checked and filled in; rows without an amount are dropped. */
fun readCsvReply(content: String?, today: String): List<CsvTx> {
    if (content.isNullOrEmpty()) throw CsvImportError("EMPTY_RESPONSE")
    var json = content.trim()
    Regex("""```(?:json)?\s*([\s\S]*?)```""").find(json)?.let { json = it.groupValues[1].trim() }
    Regex("""\[[\s\S]*\]""").find(json)?.let { json = it.value }
    val parsed = Json.parseToJsonElement(json) as? JsonArray ?: throw CsvImportError("INVALID_FORMAT")
    val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
    return parsed.map { e ->
        val o = e as? JsonObject ?: JsonObject(emptyMap())
        val amount = kotlin.math.abs(jsParseFloat(o["amount"]?.let(::jsText) ?: "")).let { if (it.isNaN()) 0.0 else it }
        val category = normalizeCategory(o["category"]?.let(::jsText))
        val payment = (o["payment"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val date = o["date"]?.let(::jsText)
        CsvTx(
            amount = amount,
            category = if (category in EXPENSE_CATEGORIES) category else "Other",
            description = (if (truthy(o["description"])) jsText(o["description"]!!) else "-").take(200),
            payment = payment?.takeIf { it in PAYMENT_METHODS } ?: "UPI",
            date = date?.takeIf { isoDate.matches(it) } ?: today,
            notes = (if (truthy(o["notes"])) jsText(o["notes"]!!) else "").take(500),
            type = if ((o["type"] as? JsonPrimitive)?.let { it.isString && it.content == "income" } == true) "income" else "expense",
        )
    }.filter { it.amount > 0 }
}

/** handleConfirmImport's "Successfully imported 3 expenses and 1 income entry!". */
fun csvDoneText(expenses: Int, income: Int): String {
    var msg = ""
    if (expenses > 0) msg += "$expenses expense${if (expenses != 1) "s" else ""}"
    if (income > 0) {
        if (msg.isNotEmpty()) msg += " and "
        msg += "$income income entr${if (income != 1) "ies" else "y"}"
    }
    return "Successfully imported $msg!"
}
