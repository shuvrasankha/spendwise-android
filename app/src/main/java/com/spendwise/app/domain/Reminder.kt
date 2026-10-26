package com.spendwise.app.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

// The daily reminder (spec §7.5, Q3): settings.remindOn / remindAt, scheduled on this device.
// Ported from reminders.js and helpers.js › reminderSync.

const val DEFAULT_REMIND_AT = "21:00"

/** reminders.js reads a time as /^\d{2}:\d{2}$/; firestore.rules › validSettings only stores 00:00–29:59. */
private val READ_TIME = Regex("^\\d{2}:\\d{2}$")
private val RULES_TIME = Regex("^[0-2][0-9]:[0-5][0-9]$")

/** The account's time if it's usable here, else null. */
fun accountRemindAt(remindAt: String?): String? =
    remindAt?.takeIf { READ_TIME.matches(it) && RULES_TIME.matches(it) && runCatching { LocalTime.parse(it) }.isSuccess }

enum class ReminderAction { UPLOAD, OFF, ON, ASK }

/**
 * helpers.js › reminderSync: what this device does when the account's switch ([remindOn], null when never set)
 * differs from its own. [permitted]: notifications are allowed here. Unlike a browser, Android can't tell "never
 * asked" from "denied", so a device without permission is told it can turn it on (ASK).
 */
fun reminderSync(remindOn: Boolean?, deviceOn: Boolean, permitted: Boolean): ReminderAction? = when {
    remindOn == null -> if (deviceOn) ReminderAction.UPLOAD else null
    remindOn == deviceOn -> null
    !remindOn -> ReminderAction.OFF
    permitted -> ReminderAction.ON
    else -> ReminderAction.ASK
}

/**
 * Everything this device does after a settings change. [deviceAt] is the time it's scheduled for, null when
 * off here. [schedule] is a time to (re)schedule for; [uploadOn] / [uploadAt] are written to the account.
 */
data class ReminderSteps(
    val schedule: String? = null,
    val cancel: Boolean = false,
    val uploadOn: Boolean = false,
    val uploadAt: String? = null,
    val ask: Boolean = false,
)

fun reminderSteps(remindOn: Boolean?, remindAt: String?, deviceAt: String?, permitted: Boolean): ReminderSteps {
    val accountAt = accountRemindAt(remindAt)
    val at = accountAt ?: deviceAt ?: DEFAULT_REMIND_AT
    val action = reminderSync(remindOn, deviceAt != null, permitted)
    // The account's time wins; a time chosen here before it had one is uploaded once (reminders.js › syncTime)
    val retime = deviceAt != null && action != ReminderAction.OFF && at != deviceAt
    return ReminderSteps(
        schedule = if (action == ReminderAction.ON || retime) at else null,
        cancel = action == ReminderAction.OFF,
        uploadOn = action == ReminderAction.UPLOAD,
        uploadAt = if (accountAt == null && deviceAt != null) deviceAt else null,
        ask = action == ReminderAction.ASK,
    )
}

/** How long until the next [at] ("HH:mm") in [now]'s zone: later today, or tomorrow once it has passed. */
fun untilNextReminder(now: ZonedDateTime, at: String): Duration {
    var next = now.with(LocalTime.parse(at)).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

fun reminderOnNote(at: String) = "You'll get a reminder at $at if you haven't added anything that day."
const val REMINDER_BLOCKED = "Notifications are blocked for SpendWise. Allow them in your phone settings, then turn this on again."
const val REMINDER_ASK = "Daily reminder is on for your account. Turn it on here to get it on this device too."
