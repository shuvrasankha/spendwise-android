package com.spendwise.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Speak, type or photograph entries (spec §7.4), ported from voice-data.js (cleanEntry, parseTags, emiCount)
// and voice-command.js (the prompts, the reply's JSON, reviewProblem). Nothing is saved until reviewed.

const val VOICE_AI_MODEL = "@cf/qwen/qwen2.5-coder-32b-instruct"
const val RECEIPT_AI_MODEL = "@cf/meta/llama-4-scout-17b-16e-instruct"

data class VoiceLanguage(val code: String, val label: String, val name: String)

val VOICE_LANGUAGES = listOf(
    VoiceLanguage("en-IN", "English", "English"), VoiceLanguage("hi-IN", "हिन्दी", "Hindi"), VoiceLanguage("bn-IN", "বাংলা", "Bengali"),
    VoiceLanguage("ta-IN", "தமிழ்", "Tamil"), VoiceLanguage("te-IN", "తెలుగు", "Telugu"), VoiceLanguage("kn-IN", "ಕನ್ನಡ", "Kannada"),
    VoiceLanguage("ml-IN", "മലയാളം", "Malayalam"), VoiceLanguage("mr-IN", "मराठी", "Marathi"), VoiceLanguage("gu-IN", "ગુજરાતી", "Gujarati"),
    VoiceLanguage("pa-IN", "ਪੰਜਾਬੀ", "Punjabi"), VoiceLanguage("or-IN", "ଓଡ଼ିଆ", "Odia"),
)

/** helpers.js › CATEGORY_EXAMPLES: what each category covers, for the AI. */
private val CATEGORY_EXAMPLES = mapOf(
    "Food & Dining" to "restaurants, cafes, Swiggy, Zomato, takeaway, snacks, tea",
    "Groceries" to "supermarkets, kirana stores, Blinkit, Zepto, BigBasket, vegetables, milk",
    "Transport" to "petrol, diesel, CNG, fuel stations, EV charging, parking, tolls, FASTag, cabs, autos, metro, bus, bike or car service",
    "Travel" to "flights, trains, IRCTC, hotels, holiday bookings",
    "Shopping" to "clothes, electronics, Amazon, Flipkart, salon, personal care, household items",
    "Bills & Utilities" to "electricity, water, gas cylinder, mobile recharge, broadband, DTH, subscriptions",
    "Home & Rent" to "rent, maintenance, repairs, furniture",
    "Health" to "pharmacy, medicines, doctor, hospital, lab tests, gym",
    "Entertainment" to "movies, events, games, streaming one-offs",
    "Education" to "school or college fees, courses, books, stationery",
    "Savings & Investments" to "SIP, mutual funds, stocks, FD, RD, insurance premiums",
    "Other" to "only when nothing above fits",
)

fun categoryGuide(): String = EXPENSE_CATEGORIES.joinToString("\n") { "- $it: ${CATEGORY_EXAMPLES[it]}" }

val DEBT_TYPES = listOf(THEY_OWE, I_OWE)
const val ENTRY_EXPENSE = "expense"
const val ENTRY_INCOME = "income"
const val ENTRY_DEBT = "debt"

/**
 * One entry on the review card, with every field its Add form has. [amountText] and [emiRaw] are what's in
 * the boxes; [amount] and [emiTotal] are what they read as.
 */
data class VoiceEntry(
    val type: String,
    val amount: Double,
    val date: String,
    val notes: String,
    val amountText: String = jsNumberText(amount),
    val category: String = "",
    val payment: String = "",
    val cardName: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val emiTotal: Int = 0,
    val emiRaw: String = "",
    val repeat: Boolean = false,
    val source: String = "",
    val paymentType: String = "",
    val bank: String = "",
    val debtType: String = "",
    val person: String = "",
    val email: String = "",
)

/** JS String(v ?? ''): what the AI sent, as text. */
private fun jsString(v: JsonElement?): String = when (v) {
    null, JsonNull -> ""
    is JsonPrimitive -> v.content
    is JsonArray -> v.joinToString(",") { jsString(it) }
    is JsonObject -> "[object Object]"
}

/** AI output is untrusted text: keep it a string and within the form fields' limits. */
private fun clip(v: JsonElement?, max: Int) = jsString(v).trim().take(max)

private val ISO_DAY = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val VOICE_EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

/** "#goa2026, wedding work" → [goa2026, wedding, work]: no #, no repeats, at most 10 of 40 characters. */
fun parseTags(text: String): List<String> =
    text.replace(',', ' ').split(Regex("\\s+")).map { it.replace(Regex("^#+"), "").trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(10)

private fun parseTags(v: JsonElement?): List<String> = parseTags(if (v is JsonArray) v.joinToString(" ") { jsString(it) } else jsString(v))

/** A whole number of EMI payments, 2 to 600, else 0. */
fun emiCount(n: Double): Int = if (n % 1.0 == 0.0 && n >= 2 && n <= 600) n.toInt() else 0
fun emiCount(text: String): Int = emiCount(text.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN })

/** cleanEntry: one entry the AI read, checked and filled in with the Add forms' defaults; null when unusable. */
fun cleanEntry(o: JsonElement?, today: String): VoiceEntry? {
    if (o !is JsonObject) return null
    val type = (o["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (type !in listOf(ENTRY_EXPENSE, ENTRY_INCOME, ENTRY_DEBT)) return null
    val amount = jsParseFloat(jsString(o["amount"]))
    if (!(amount > 0)) return null
    val date = (o["date"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { ISO_DAY.matches(it) } ?: today
    val e = VoiceEntry(type!!, amount, date, clip(o["notes"], 500))
    fun str(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return when (type) {
        ENTRY_EXPENSE -> {
            // The AI may still use an older name such as "Rent", or a near miss
            var category = normalizeCategory(jsString(o["category"]))
            if (category !in EXPENSE_CATEGORIES) {
                val lower = jsString(o["category"]).lowercase()
                category = EXPENSE_CATEGORIES.firstOrNull { lower.isNotEmpty() && (it.lowercase().contains(lower) || lower.contains(it.lowercase())) } ?: "Other"
            }
            val payment = str("payment")?.takeIf { it in PAYMENT_METHODS } ?: DEFAULT_PAYMENT
            val emi = emiCount(jsNumberOf(o["emiTotal"]))
            val r = o["repeat"] as? JsonPrimitive
            val repeat = r?.booleanOrNull == true && !r.isString || (r?.isString == true && r.content == "monthly") || emi > 0
            e.copy(
                category = category, payment = payment, cardName = if (isCard(payment)) clip(o["cardName"], 100) else "",
                description = clip(o["description"], 100).ifEmpty { "-" }, tags = parseTags(o["tags"]),
                emiTotal = if (repeat) emi else 0, emiRaw = if (repeat && emi > 0) emi.toString() else "", repeat = repeat,
            )
        }
        ENTRY_INCOME -> e.copy(
            source = clip(o["source"], 100).ifEmpty { "Other" },
            paymentType = str("paymentType")?.takeIf { it in INCOME_PAYMENT_TYPES } ?: "Online",
            bank = clip(o["bank"], 100),
        )
        else -> e.copy(
            debtType = str("debtType")?.takeIf { it in DEBT_TYPES } ?: THEY_OWE,
            person = clip(o["person"], 100),
            email = clip(o["email"], 254).lowercase().takeIf { VOICE_EMAIL.matches(it) } ?: "",
        )
    }
}

/** handleTextSubmit: a bank SMS read on the device, as the review card's entry (validateEntries([sms])). */
fun voiceEntryFrom(sms: SmsEntry): VoiceEntry = when (sms) {
    is SmsEntry.Spent -> VoiceEntry(
        ENTRY_EXPENSE, sms.amount, sms.date, "", category = sms.category, payment = sms.payment,
        cardName = if (isCard(sms.payment)) sms.cardName.take(100) else "", description = sms.description.take(100).ifEmpty { "-" },
    )
    is SmsEntry.Earned -> VoiceEntry(ENTRY_INCOME, sms.amount, sms.date, "", source = sms.source.take(100), paymentType = sms.paymentType, bank = sms.bank.take(100))
}

/** callAIModel: the reply's JSON (maybe in a code block) as a list of entries; throws when it isn't JSON. */
fun readEntries(content: String): List<JsonElement> {
    var json = content.trim()
    Regex("""```(?:json)?\s*([\s\S]*?)```""").find(json)?.let { json = it.groupValues[1].trim() }
    json = (Regex("""\[[\s\S]*\]""").find(json) ?: Regex("""\{[\s\S]*\}""").find(json))?.value ?: json
    val parsed = Json.parseToJsonElement(json)
    return if (parsed is JsonArray) parsed else listOf(parsed)
}

/** reviewProblem: the first entry that can't be saved yet, as "Entry 2: …" when there are several; null when all are fine. */
fun reviewProblem(entries: List<VoiceEntry>, myEmail: String): String? {
    entries.forEachIndexed { i, e ->
        val msg = when {
            !(e.amount > 0) -> "Enter an amount above zero."
            !ISO_DAY.matches(e.date) -> "Pick a date."
            e.type == ENTRY_DEBT && e.person.isEmpty() -> "Add who the debt is with."
            e.type == ENTRY_DEBT && emailError(e.email, myEmail) != null -> emailError(e.email, myEmail)
            e.type == ENTRY_EXPENSE && e.repeat && e.emiRaw.isNotEmpty() && e.emiTotal == 0 -> "Total payments should be a whole number from 2 to 600."
            else -> null
        }
        if (msg != null) return if (entries.size > 1) "Entry ${i + 1}: $msg" else msg
    }
    return null
}

/** The status after reading: one entry, or how many were found. */
fun foundText(count: Int) = if (count == 1) "Check it and fix anything that's off, then save." else "Found $count entries. Check them and fix anything that's off, then save."

/** buildPrompt's system message. */
fun voicePrompt(today: LocalDate, lang: VoiceLanguage): String {
    val t = today.toString()
    val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.US)
    return """You are a financial assistant for SpendWise, an expense, income, and debt tracking app. Your job is to parse natural language into structured JSON.

Today is $dayOfWeek, $t ($monthName ${today.dayOfMonth}, ${today.year}).

IMPORTANT: The user may speak in ANY language including ${lang.label} (${lang.code}). You MUST understand the input regardless of language and always return JSON with field values in English.
Common multilingual amount keywords: "Rs", "₹", "रुपये", "টাকা", "ரூபாய்", "రూపాయలు", etc.
Common date words: "आज/today", "कल/yesterday", "আজ", "இன்று", "నేడు", etc.

RULES:
1. The user may describe ONE or MULTIPLE transactions in a single sentence. You MUST extract ALL of them.
2. For each transaction, determine if it is an EXPENSE, INCOME, or DEBT.
3. Extract the amount, date, and other relevant fields for EACH transaction separately.
4. If no date is mentioned, use today's date: $t
5. If the user says "yesterday" (or its equivalent in any language), compute the correct date.
6. For expenses, pick the best matching category, using its exact name from this list:
${categoryGuide()}
7. For expenses, pick payment method from: ${PAYMENT_METHODS.joinToString(", ")}. Default to "UPI" if not mentioned.
   - cardName: the card's name when one is mentioned with a card payment (e.g. "HDFC Regalia"), else "".
   - tags: words the user marks as tags or a trip/event the spending belongs to ("for the Goa trip" → ["goa"], "tag wedding" → ["wedding"]), else [].
   - repeat: true when it is paid every month (rent, a subscription, an SIP, an EMI, "every month", "monthly"), else false.
   - emiTotal: for an EMI or loan with a stated number of monthly payments ("12 month EMI" → 12), else 0.
   - notes: anything else the user wants remembered about it, else "".
8. For income, determine source (e.g. "Salary", "Freelance", "Business", etc.), paymentType ("Online" or "Cash"), bank or wallet if mentioned (e.g. "HDFC", "Paytm"), and notes.
   For debts, also take the person's email address if the user gives one, else "".
9. For debts, determining the type is CRITICAL. You MUST carefully distinguish between:
   
   ➤ "they-owe" (someone OWES the user / user LENT or GAVE money):
     - English: "I lent", "I gave", "they owe me", "I loaned", "I paid for them", "advanced"
     - Hindi: "मैंने दिया", "उसने माँगा", "उसे देना है", "मैंने उधार दिया"
     - Bengali: "আমি দিয়েছি", "ওকে ধার দিয়েছি"
     - Tamil: "நான் கொடுத்தேன்", "அவன் வாங்கினான்"
     - Keywords: LENT, GAVE, LOANED, PAID FOR, ADVANCED, OWES ME
   
   ➤ "i-owe" (user OWES someone / user BORROWED or TOOK money):
     - English: "I borrowed", "I took", "I owe them", "I need to pay back", "got from"
     - Hindi: "मैंने उधार लिया", "मुझे देना है", "मैंने माँगा"
     - Bengali: "আমি ধার নিয়েছি", "আমাকে দিতে হবে"
     - Tamil: "நான் வாங்கினேன்", "எனக்கு தரனும்"
     - Keywords: BORROWED, TOOK, OWE THEM, NEED TO PAY, GOT FROM
   
   IMPORTANT RULES for debt type:
   - "I gave 5000 to John" → they-owe (you gave money, John owes you)
   - "I took 5000 from John" → i-owe (you took money, you owe John)
   - "I lent 5000 to John" → they-owe (you lent money, John owes you)
   - "I borrowed 5000 from John" → i-owe (you borrowed, you owe John)
   - "Paid 500 for friend's ticket" → they-owe (you paid for someone, they owe you)
   - "Got 1000 from mom" → i-owe (you received money, you owe mom)
10. Return ONLY valid JSON, no markdown, no explanation.
11. All JSON field values MUST be in English, regardless of input language.
12. ALWAYS return a JSON ARRAY (even for a single transaction).

For EXPENSE entries, each element:
{"type":"expense","amount":<number>,"category":"<category>","date":"YYYY-MM-DD","description":"<short description in English>","payment":"<payment method>","cardName":"<card name or empty>","notes":"<notes or empty>","tags":[<tag words>],"repeat":<true|false>,"emiTotal":<number or 0>}

For INCOME entries, each element:
{"type":"income","amount":<number>,"source":"<source in English>","date":"YYYY-MM-DD","paymentType":"<Online|Cash>","bank":"<bank name or empty>","notes":""}

For DEBT entries, each element:
{"type":"debt","amount":<number>,"debtType":"<they-owe|i-owe>","person":"<person name>","email":"<their email or empty>","date":"YYYY-MM-DD","notes":"<optional notes>"}

Example input: "today I spent 30 rs on breakfast and 100 on lunch. I also bought a fan from Amazon using credit card for 2000 rs"
Example output: [{"type":"expense","amount":30,"category":"Food & Dining","date":"$t","description":"Breakfast","payment":"UPI","cardName":"","notes":""},{"type":"expense","amount":100,"category":"Food & Dining","date":"$t","description":"Lunch","payment":"UPI","cardName":"","notes":""},{"type":"expense","amount":2000,"category":"Shopping","date":"$t","description":"Fan from Amazon","payment":"Credit Card","cardName":"","notes":""}]

Example input: "I lent 5000 to John yesterday"
Example output: [{"type":"debt","amount":5000,"debtType":"they-owe","person":"John","email":"","date":"<yesterday's date>","notes":""}]

Example input: "Paid 1200 rent for the flat every month, and 3500 phone EMI on my HDFC Millennia credit card, 12 months, tag gadgets"
Example output: [{"type":"expense","amount":1200,"category":"Home & Rent","date":"$t","description":"Flat rent","payment":"UPI","cardName":"","notes":"","tags":[],"repeat":true,"emiTotal":0},{"type":"expense","amount":3500,"category":"Shopping","date":"$t","description":"Phone EMI","payment":"Credit Card","cardName":"HDFC Millennia","notes":"","tags":["gadgets"],"repeat":true,"emiTotal":12}]

Example input: "Got 20000 from a freelance project into my SBI account, note invoice 42"
Example output: [{"type":"income","amount":20000,"source":"Freelance","date":"$t","paymentType":"Online","bank":"SBI","notes":"Invoice 42"}]

Example input: "I borrowed 2000 from Mom"
Example output: [{"type":"debt","amount":2000,"debtType":"i-owe","person":"Mom","date":"$t","notes":""}]

Example input: "I gave 3000 rupees to Ravi for dinner"
Example output: [{"type":"debt","amount":3000,"debtType":"they-owe","person":"Ravi","date":"$t","notes":"For dinner"}]

Example input: "I took 10000 from my brother"
Example output: [{"type":"debt","amount":10000,"debtType":"i-owe","person":"Brother","date":"$t","notes":""}]

Example input: "Paid 500 for my friend's movie ticket"
Example output: [{"type":"debt","amount":500,"debtType":"they-owe","person":"Friend","date":"$t","notes":"Movie ticket"}]"""
}

/** scanReceipt's system message. */
fun receiptPrompt(today: String): String = """You read shop receipts and bills for an Indian expense tracker. Today is $today.
Reply with ONLY one JSON object, no other text:
{"type":"expense","amount":<the final total paid, a number>,"date":"YYYY-MM-DD","category":"<exact name from the list below>","payment":"<one of: ${PAYMENT_METHODS.joinToString(", ")}, or leave out if not shown>","description":"<shop or merchant name, under 60 characters>"}
Use the grand total after taxes and discounts, never a subtotal or a single item. If the date can't be read, use null.
If the photo is not a receipt or bill, reply {"type":"none"}.
Pick the category from what the shop sells, using its exact name:
${categoryGuide()}"""

/** toDocData: the reviewed entry as its Add form's input. */
fun VoiceEntry.expenseInput() = ExpenseInput(
    amount, category, date, payment, if (isCard(payment)) cardName else "", description.ifEmpty { "-" }, notes, tags,
    repeat = if (repeat) "monthly" else null, emiTotal = if (repeat && emiTotal > 0) emiTotal else null,
)
fun VoiceEntry.incomeInput() = IncomeInput(amount, date, source, paymentType, bank, notes)
fun VoiceEntry.debtInput() = DebtInput(amount, debtType, person, date, notes, email)

/** saveEntry's "₹500 saved" or "3 entries saved · ₹1,200". */
fun voiceSaved(entries: List<VoiceEntry>, money: (Double) -> String): String {
    val total = money(entries.sumOf { it.amount })
    return if (entries.size == 1) "$total saved" else "${entries.size} entries saved · $total"
}

/** updateSaveButton: "Save · ₹500" or "Save 3 · ₹1,200", counting only amounts above zero. */
fun voiceSaveLabel(entries: List<VoiceEntry>, money: (Double) -> String): String {
    val total = money(entries.sumOf { if (it.amount > 0) it.amount else 0.0 })
    return if (entries.size == 1) "Save · $total" else "Save ${entries.size} · $total"
}

/** onPreviewInput › amount: digits and dots only, else 0. */
fun voiceAmount(text: String): Double = jsParseFloat(text.replace(Regex("[^\\d.]"), "")).let { if (it.isNaN()) 0.0 else it }

const val VOICE_EMPTY = "Please type or speak a command first."
const val VOICE_FROM_SMS = "Read from your SMS on this device. Check it, fix anything that's off, then save."
const val VOICE_OFFLINE = "You're offline. Spoken and typed commands need a connection, but a pasted bank SMS works offline."
const val VOICE_BUSY = "AI is analyzing your command…"
const val VOICE_NONE = "Could not understand the command. Please try rephrasing."
const val VOICE_RATE_LIMITED = "Daily AI limit reached. Please try again later."
const val VOICE_MODEL_LOADING = "AI model is loading. Please try again in 20-30 seconds."
const val VOICE_FAILED = "Something went wrong reading that. Nothing was saved, so please try again."
const val VOICE_SAVE_FAILED = "Couldn't save. Nothing was saved, so it's safe to try again."
