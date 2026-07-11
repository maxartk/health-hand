package com.healthhand.admin.ui.catalog

private val TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

fun validateService(name: String, durationMinutes: Int, price: Int): String? = when {
    name.isBlank() -> "Вкажіть назву послуги"
    name.trim().length < 3 -> "Назва має містити щонайменше 3 символи"
    durationMinutes !in 15..480 -> "Тривалість має бути від 15 до 480 хв"
    price !in 1..100_000 -> "Ціна має бути від 1 до 100 000 грн"
    else -> null
}

fun validateShift(employeeId: Int, startTime: String, endTime: String): String? = when {
    employeeId <= 0 -> "Оберіть майстра"
    !TIME_PATTERN.matches(startTime) || !TIME_PATTERN.matches(endTime) -> "Час має бути у форматі HH:MM"
    endTime <= startTime -> "Час завершення має бути пізніше початку"
    else -> null
}
