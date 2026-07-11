package com.healthhand.admin.ui.bookings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppointmentValidationTest {
    @Test
    fun rejectsBlankAndArbitraryStart() {
        assertEquals("Вкажіть початок у форматі 2026-07-12T10:00:00+03:00", validateAppointment("", ""))
        assertEquals("Вкажіть початок у форматі 2026-07-12T10:00:00+03:00", validateAppointment("завтра", ""))
    }

    @Test
    fun rejectsInvalidOrEarlierEnd() {
        assertEquals("Вкажіть завершення у форматі 2026-07-12T11:00:00+03:00", validateAppointment("2026-07-12T10:00:00+03:00", "soon"))
        assertEquals("Завершення має бути пізніше початку", validateAppointment("2026-07-12T10:00:00+03:00", "2026-07-12T09:00:00+03:00"))
    }

    @Test
    fun acceptsOffsetAndUtcTimesWithOptionalEnd() {
        assertNull(validateAppointment("2026-07-12T10:00:00+03:00", ""))
        assertNull(validateAppointment("2026-07-12T07:00:00Z", "2026-07-12T08:00:00Z"))
    }
}
