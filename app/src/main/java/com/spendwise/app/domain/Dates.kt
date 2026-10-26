package com.spendwise.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

// Ported from helpers.js › todayStr, addMonths, getWeekStart (spec §3.0, §6.1)

/** Local-calendar "YYYY-MM-DD". Never derive it from UTC. */
fun todayStr(today: LocalDate = LocalDate.now()): String = today.toString()

/** "YYYY-MM" shifted by [n] months. */
fun addMonths(month: String, n: Int): String = YearMonth.parse(month).plusMonths(n.toLong()).toString()

/** The Monday on or before [today], as "YYYY-MM-DD". Weeks start on Monday. */
fun weekStart(today: LocalDate = LocalDate.now()): String =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
