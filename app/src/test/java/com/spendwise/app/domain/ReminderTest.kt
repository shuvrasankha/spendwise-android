package com.spendwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** The daily reminder: helpers.js › reminderSync's cases, what a device does after a settings change, and when it fires. */
class ReminderTest {

    @Test fun reminderSync_likeTheWeb() {
        assertEquals(ReminderAction.UPLOAD, reminderSync(null, deviceOn = true, permitted = true))
        assertNull(reminderSync(null, deviceOn = false, permitted = true))
        assertNull(reminderSync(true, deviceOn = true, permitted = true))
        assertNull(reminderSync(false, deviceOn = false, permitted = true))
        assertEquals(ReminderAction.OFF, reminderSync(false, deviceOn = true, permitted = true))
        assertEquals(ReminderAction.ON, reminderSync(true, deviceOn = false, permitted = true))
        assertEquals(ReminderAction.ASK, reminderSync(true, deviceOn = false, permitted = false))
    }

    @Test fun steps_onElsewhereStartsHereAtTheAccountsTime() =
        assertEquals(ReminderSteps(schedule = "20:30"), reminderSteps(true, "20:30", deviceAt = null, permitted = true))

    @Test fun steps_onElsewhereWithoutPermissionAsks() =
        assertEquals(ReminderSteps(ask = true), reminderSteps(true, "20:30", deviceAt = null, permitted = false))

    @Test fun steps_offElsewhereStopsHere() =
        assertEquals(ReminderSteps(cancel = true), reminderSteps(false, "20:30", deviceAt = "20:30", permitted = true))

    @Test fun steps_timeChangedElsewhereReschedules() =
        assertEquals(ReminderSteps(schedule = "07:15"), reminderSteps(true, "07:15", deviceAt = "21:00", permitted = true))

    @Test fun steps_inStepDoesNothing() =
        assertEquals(ReminderSteps(), reminderSteps(true, "21:00", deviceAt = "21:00", permitted = true))

    @Test fun steps_neverSetUploadsThisDevicesSwitchAndTime() =
        assertEquals(ReminderSteps(uploadOn = true, uploadAt = "22:00"), reminderSteps(null, null, deviceAt = "22:00", permitted = true))

    @Test fun steps_badAccountTimeUsesThisDevicesOrTheDefault() {
        assertEquals(ReminderSteps(uploadAt = "22:00"), reminderSteps(true, "9pm", deviceAt = "22:00", permitted = true))
        assertEquals(ReminderSteps(schedule = DEFAULT_REMIND_AT), reminderSteps(true, "25:00", deviceAt = null, permitted = true))
        assertNull(accountRemindAt("29:59")) // the rules accept it, but it isn't a time
        assertEquals("00:00", accountRemindAt("00:00"))
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 10, 26, h, m, s, 0, zone)

    @Test fun nextReminder_laterTodayOrTomorrow() {
        assertEquals(Duration.ofMinutes(90), untilNextReminder(at(19, 30), "21:00"))
        assertEquals(Duration.ofHours(24), untilNextReminder(at(21, 0), "21:00")) // exactly now: tomorrow's
        assertEquals(Duration.ofHours(24).minusSeconds(30), untilNextReminder(at(21, 0, 30), "21:00"))
        assertEquals(Duration.ofMinutes(15), untilNextReminder(at(23, 50), "00:05"))
    }

    @Test fun nextReminder_acrossADaylightSavingChange() {
        // Berlin: clocks go back an hour at 03:00 on 25 Oct 2026, so 21:00 to 21:00 the next day is 25 hours
        val berlin = ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(Duration.ofHours(25), untilNextReminder(berlin, "21:00"))
    }

    @Test fun notes() {
        assertEquals("You'll get a reminder at 21:00 if you haven't added anything that day.", reminderOnNote("21:00"))
    }
}
