package com.healthhand.admin.ui.bookings

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

private fun parseAppointmentTime(value: String): Instant? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC) }.getOrNull()
}

fun validateAppointment(startAt: String, endAt: String): String? {
    val start = parseAppointmentTime(startAt)
        ?: return "Вкажіть початок у форматі 2026-07-12T10:00:00+03:00"
    if (endAt.isBlank()) return null
    val end = parseAppointmentTime(endAt)
        ?: return "Вкажіть завершення у форматі 2026-07-12T11:00:00+03:00"
    return if (end <= start) "Завершення має бути пізніше початку" else null
}
