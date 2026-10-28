package com.spendwise.app.data.worker

import com.spendwise.app.domain.UpcomingBill
import org.junit.Assert.assertEquals
import org.junit.Test

// The shapes worker/src/push.js validates.
class PushBodiesTest {
    @Test fun `subscribe and unsubscribe`() {
        assertEquals("""{"endpoint":"fcm:t","remindAt":"21:00","tz":"Asia/Kolkata"}""", subscribeBody("fcm:t", "21:00", "Asia/Kolkata").toString())
        assertEquals("""{"endpoint":"fcm:t"}""", endpointBody("fcm:t").toString())
    }

    @Test fun `bills and months`() {
        assertEquals(
            """{"bills":[{"key":"k","title":"Rent","amount":500.5,"due":"2026-11-01"}],"symbol":"₹"}""",
            billsBody(listOf(UpcomingBill("k", "Rent", 500.5, "2026-11-01")), "₹").toString(),
        )
        assertEquals("""{"totals":{"2026-10":151,"2026-09":11},"symbol":"$"}""", monthsBody(linkedMapOf("2026-10" to 151L, "2026-09" to 11L), "$").toString())
    }
}
