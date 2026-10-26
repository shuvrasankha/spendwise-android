package com.spendwise.app.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

// The AI coach on Insights (spec §1.7), ported from insights-v2.js (TYPES, systemPrompt, errorText) and
// insights-data.js (factsText, renderMarkdown). Only numbers go out: totals by category, dates, counts.

const val COACH_AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
const val COACH_MAX_TURNS = 8
const val COACH_SAVED_MS = 24 * 3_600_000L
const val COACH_NOTE = "Ask about the numbers above. Answers are written by AI and can be wrong."
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
        "the savings rate against a 20% guideline and concrete steps to raise it. If no income is recorded, say that income entries are needed for this and give spending-based tips instead.",
        "How is my savings rate, and how can I improve it?",
        listOf("How much should I save each month?", "What if I cut my top category by 10%?", "Where do small amounts add up?"),
    ),
    TRENDS(
        "Trends", "What changed?",
        "how this period compares with the previous one: total and per-category changes, the daily average and the month-end projection, and what is driving the change.",
        "How does this period compare with before, and what is driving the change?",
        listOf("Which category changed the most?", "Will I spend more than last month?", "What should I watch next month?"),
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

fun coachSystemPrompt(topic: CoachTopic, facts: String, currency: Currency): String = """You are the money coach inside SpendWise, a personal expense tracker. Amounts are in ${currency.code} (${currency.symbol}).
Rules:
- Use only the numbers below. Never invent amounts, dates, income, goals or interest rates. If something isn't in the data, say so in one short sentence.
- Be specific: name categories and amounts from the data, and estimate savings in ${currency.symbol}.
- Keep each answer under 150 words. Use a short numbered or bulleted list for steps.
- Plain, friendly English. Don't recommend specific financial products, funds or stocks.
Focus: ${topic.focus}

USER'S NUMBERS
$facts"""

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
