package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal
import java.math.RoundingMode

// The AI coach on Insights (spec §1.7), ported from insights-v2.js (TYPES, systemPrompt, errorText) and
// insights-data.js (factsText, renderMarkdown). Only numbers go out: totals by category, dates, counts.

const val COACH_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
const val COACH_MAX_TURNS = 8
const val COACH_SAVED_MS = 24 * 3_600_000L
const val COACH_NO_DATA = "Add some expenses or income for this period, then ask the coach about them."

enum class CoachTopic(val title: String, val chip: String, val focus: String, val question: String, val suggestions: List<String>) {
    OVERVIEW(
        "Overview", "What matters most?",
        "the 3 things that matter most in this period: one thing going well, one thing to watch, and one action for the coming week.",
        "What are the 3 most important things in my numbers right now?",
        listOf("What is going well?", "Where can I save the most?", "Am I spending more than before?"),
    ),
    SPENDING(
        "Spending", "Where can I cut back?",
        "where the money went and 2–3 realistic cuts, biggest first, with roughly how much each could save per month.",
        "Where is my money going, and where could I realistically cut back?",
        listOf("Which category grew the most?", "How much could I save on food?", "Is my biggest expense normal?"),
    ),
    SAVINGS(
        "Savings", "How are my savings?",
        "the savings rate against a 20% guideline and concrete steps to raise it. If no income is recorded, say that income entries are needed for this and give spending-based tips instead. For a question about investments, use INVESTMENTS: what is held by type, put in, worth, gain and SIPs.",
        "How is my savings rate, and how can I improve it?",
        listOf("How much should I save each month?", "What if I cut my top category by 10%?", "Where do small amounts add up?"),
    ),
    TRENDS(
        "Trends", "What changed?",
        "how this period compares with the previous one: total and per-category changes, the daily average and the month-end projection, and what is driving the change.",
        "How does this period compare with before, and what is driving the change?",
        listOf("Which category changed the most?", "Will I spend more than last month?", "What should I watch next month?"),
    ),
    BUDGET(
        "Budget", "Suggest a budget",
        "a realistic monthly budget from the spending in the numbers: a total and a limit for the main categories, each rounded, with one line on why. Base it on the monthly amounts the numbers support.",
        "Suggest a monthly budget based on my spending.",
        listOf("Which limit will be hardest?", "How much could I save with this?", "What should I cut first?"),
    ),
    DEBT(
        "Debt", "What about my debts?",
        "the unsettled debts in the numbers: what to settle first, money others owe the user that is worth following up, and a simple plan. If there are no unsettled debts, say so in one sentence and give one tip.",
        "What should I do about my debts?",
        listOf("What should I pay back first?", "How do I follow up on money owed to me?", "Should I pay debts before saving?"),
    ),
}

@Serializable
data class ChatTurn(val role: String, val content: String)

/** saveChat: one conversation per user, about the numbers it started from, for a day. */
@Serializable
data class SavedChat(val uid: String, val savedAt: Long, val topic: String, val label: String, val facts: String, val history: List<ChatTurn>)

/** JS n.toFixed(d): the exact binary value, halves away from zero, and "-0" for a small negative. */
fun jsToFixed(n: Double, digits: Int): String =
    (if (n < 0) "-" else "") + BigDecimal(kotlin.math.abs(n)).setScale(digits, RoundingMode.HALF_UP).toPlainString()

private fun signed(n: Double) = (if (n >= 0) "+" else "") + jsToFixed(n, 0)

/** factsText: the numbers the coach sees. No descriptions, notes or names. */
fun factsText(s: InsightSummary, r: PeriodRange, money: (Double) -> String): String {
    val lines = mutableListOf(
        "PERIOD: ${r.label}" + (r.monthDays?.let { " — ${r.elapsedDays} of $it days so far" } ?: ""),
        "SPENT: ${money(s.spent)} in ${s.txCount} expenses (${money(s.dailyAvg)} per day)",
        "INCOME: " + if (s.incomeCount > 0) "${money(s.earned)} from ${s.incomeCount} entries" else "none recorded",
    )
    if (s.invested > 0) lines += "INVESTED: ${money(s.invested)} (counted as saved, not as spending)"
    if (s.earned > 0) {
        val rate = (s.earned - s.spent) / s.earned * 100
        lines += "SAVED: ${money(s.earned - s.spent)} (${jsToFixed(rate, 1)}% of income${if (s.invested > 0) ", including what was invested" else ""})"
    }
    r.monthDays?.let { lines += "ON TRACK TO SPEND THIS MONTH: ${money(s.dailyAvg * it)}" }
    lines += "COMPARED WITH ${r.prevLabel.uppercase()}: spent ${money(s.prevSpent)}" + (s.spendChange?.let { " (${signed(it)}%)" } ?: "")
    if (s.categories.isNotEmpty()) {
        lines += "BY CATEGORY: " + s.categories.joinToString("; ") { c ->
            "${c.category} ${money(c.amount)} (${jsToFixed(c.share, 0)}%" +
                (c.change?.let { ", ${signed(it)}% vs before" } ?: if (c.prevAmount == 0.0) ", new" else "") + ")"
        }
    }
    if (s.txCount > 0) {
        lines += "BY WEEKDAY: " + s.weekdays.joinToString("; ") { "${it.day} ${money(it.amount)}" }
        lines += "BY PAYMENT METHOD: " + s.payments.joinToString("; ") { "${it.name} ${money(it.amount)} (${jsToFixed(it.share, 0)}%)" }
        lines += "FIXED (repeating bills and EMIs): ${money(s.fixed)}; FLEXIBLE: ${money(s.spent - s.fixed)}"
    }
    if (s.biggest.isNotEmpty()) lines += "BIGGEST EXPENSES: " + s.biggest.joinToString("; ") { "${it.category} ${money(it.amount)} on ${insightDate(it.date)}" }
    lines += if (s.activeDebts > 0) "DEBTS NOW: user owes ${money(s.youOwe)}; others owe the user ${money(s.owedToYou)} (${s.activeDebts} unsettled)"
    else "DEBTS NOW: none unsettled"
    return lines.joinToString("\n")
}

fun coachSystemPrompt(topic: CoachTopic, facts: String, currency: Currency): String = """You are the money assistant inside SpendWise, a personal expense tracker. Amounts are in ${currency.code} (${currency.symbol}).
You can answer any personal finance question: the user's own spending, income, savings, investments and debts, and general topics like budgeting, saving, emergency funds, loans, credit, taxes, insurance and investing concepts.
Rules:
- For the user's own figures, use only the numbers below. Never invent their amounts, dates, income, goals or interest rates. If something isn't in the data, say so briefly.
- For general questions, answer from sound, widely accepted financial knowledge, and link it to the user's numbers when they're relevant.
- Talk like a knowledgeable friend: answer the actual question directly in natural sentences. Never open with a bare "Yes.", "No." or a lone number, and don't repeat a fixed template.
- Be specific: name categories and amounts from the data, and estimate savings in ${currency.symbol}.
- A saving per month can never be more than what's spent per month on that thing: check PER MONTH below. Say when an amount is already small.
- Keep answers under 180 words. Use a short list only when giving steps or options.
- Don't recommend specific financial products, funds or stocks by name. If a question isn't about money, say briefly that you only help with finances.
${if (topic == CoachTopic.OVERVIEW) "" else "Focus if relevant: ${topic.focus}\n"}- After the answer, on its own last line, write "$FOLLOW_UPS_TAG" and 2 or 3 short questions the user might ask next about their money, separated by " | ". Each under 60 characters, in the user's voice.

USER'S NUMBERS
$facts"""

const val FOLLOW_UPS_TAG = "NEXT:"

/** A past answer as the model wrote it: its follow-up line put back. */
fun withFollowUps(answer: String, next: List<String>) = if (next.isEmpty()) answer else "$answer\n\n$FOLLOW_UPS_TAG ${next.joinToString(" | ")}"

/**
 * The newest question with a reminder of the follow-up line. Tested 2026-11-06 on Groq/qwen: after a few
 * earlier answers without one, the model drops it despite the system prompt; the reminder brings it back.
 */
fun remindFollowUps(messages: List<Pair<String, String>>): List<Pair<String, String>> {
    val i = messages.indexOfLast { it.first == "user" }
    if (i < 0) return messages
    return messages.toMutableList().also { it[i] = "user" to "${messages[i].second}\n\n(End your reply with the $FOLLOW_UPS_TAG line.)" }
}

/** The answer without its follow-up line, and the questions on it. A reply without one is all answer. */
fun splitFollowUps(reply: String): Pair<String, List<String>> {
    val lines = reply.trimEnd().lines()
    val i = lines.indexOfLast { it.trim().trimStart('*', '_').startsWith(FOLLOW_UPS_TAG, ignoreCase = true) }
    if (i < 0) return reply.trim() to emptyList()
    val qs = lines[i].trim().trimStart('*', '_').substring(FOLLOW_UPS_TAG.length).trim('*', '_', ' ')
        .split('|').map { it.trim().trim('"', '-', ' ') }.filter { it.length in 3..80 }.distinct().take(3)
    return (lines.take(i) + lines.drop(i + 1)).joinToString("\n").trim() to qs
}

enum class AiFailure { RATE_LIMITED, NOT_AUTHENTICATED, OFFLINE, OTHER }

/** errorText; [resetAt] is when the daily limit resets (midnight UTC), in the device's time format. */
fun coachErrorText(f: AiFailure, resetAt: String): String = when (f) {
    AiFailure.RATE_LIMITED -> "Today's free AI limit is used up. It resets at $resetAt. The numbers above are still up to date."
    AiFailure.NOT_AUTHENTICATED -> "Please log in again to use the AI coach."
    AiFailure.OFFLINE -> "Couldn't reach the AI coach. Check your connection and try again."
    AiFailure.OTHER -> "The AI coach didn't answer. Try again."
}

// ── renderMarkdown, as blocks the screen draws ──
// Inline text carries markers: BOLD_ON…BOLD_OFF and ITALIC_ON…ITALIC_OFF.
const val BOLD_ON = '\u0001'
const val BOLD_OFF = '\u0002'
const val ITALIC_ON = '\u0003'
const val ITALIC_OFF = '\u0004'

sealed interface MdBlock {
    data class Heading(val text: String) : MdBlock
    data class Paragraph(val lines: List<String>) : MdBlock
    data class Items(val ordered: Boolean, val items: List<String>) : MdBlock
}

private val HEADING = Regex("""^#{1,6}\s+(.*)$""")
private val BULLET = Regex("""^[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^\d+[.)]\s+(.*)$""")
private val BOLD = Regex("""\*\*(.+?)\*\*""")
private val ITALIC = Regex("""(^|[^*])\*([^*\s][^*]*?)\*(?!\*)""")
private fun inline(s: String) = ITALIC.replace(BOLD.replace(s) { "$BOLD_ON${it.groupValues[1]}$BOLD_OFF" }) { "${it.groupValues[1]}$ITALIC_ON${it.groupValues[2]}$ITALIC_OFF" }

/** Minimal, safe markdown for AI answers: headings, bullet and numbered lists, **bold**, *italic*, paragraphs. */
fun markdownBlocks(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var list: MdBlock.Items? = null
    val para = mutableListOf<String>()
    fun flushPara() { if (para.isNotEmpty()) { out += MdBlock.Paragraph(para.toList()); para.clear() } }
    fun flushList() { list?.let { out += it }; list = null }
    for (raw in text.split("\n")) {
        val line = raw.trim()
        if (line.isEmpty()) { flushPara(); flushList(); continue }
        val heading = HEADING.find(line)
        val item = BULLET.find(line) ?: NUMBERED.find(line)
        if (heading != null) {
            flushPara(); flushList()
            out += MdBlock.Heading(inline(heading.groupValues[1]))
        } else if (item != null) {
            val ordered = line[0] in '0'..'9'
            flushPara()
            if (list?.ordered != ordered) flushList()
            list = (list ?: MdBlock.Items(ordered, emptyList())).let { it.copy(items = it.items + inline(item.groupValues[1])) }
        } else {
            flushList()
            para += inline(line)
        }
    }
    flushPara(); flushList()
    return out.ifEmpty { listOf(MdBlock.Paragraph(listOf("No response."))) }
}

/** Ask › photo with no question typed. */
const val BILL_QUESTION = "Explain this bill or statement"

/** Ask about a photo of a bill or statement: the image is read once and never kept. */
fun billPrompt(currency: Currency) = """You are the money assistant inside SpendWise. The user sends a photo of a bill, invoice, bank or card statement, or loan document, with a question about it. The user's currency is ${currency.code} (${currency.symbol}).
Rules:
- Answer the user's question first. Without one, explain what the document is, the total or amount due, the due date, and any fees, interest, penalties or charges worth checking.
- Use only what you can read in the photo. If something is cut off or unreadable, say so. Never guess amounts or dates.
- Never repeat account, card, phone or customer numbers, or addresses.
- Point out anything unusual: a late fee, a price rise, a charge that looks doubled, or a minimum payment that only covers interest.
- If the photo isn't a financial document, say briefly that you can only explain bills and statements.
- Keep it under 180 words, in plain sentences; a short list only for several charges.
- After the answer, on its own last line, write "$FOLLOW_UPS_TAG" and 2 or 3 short questions the user might ask next about this document, separated by " | ". Each under 60 characters, in the user's voice."""

/** The question and the photo, as one user message of parts (as receiptMessages). */
fun billMessages(currency: Currency, question: String, image: String): List<Pair<String, JsonElement>> = listOf(
    "system" to JsonPrimitive(billPrompt(currency)),
    "user" to buildJsonArray {
        add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(question)) })
        add(buildJsonObject {
            put("type", JsonPrimitive("image_url"))
            put("image_url", buildJsonObject { put("url", JsonPrimitive(image)) })
        })
    },
)
