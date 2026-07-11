package com.healthhand.admin.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogValidationTest {
    @Test
    fun serviceRejectsBlankName() {
        assertEquals("Вкажіть назву послуги", validateService("  ", 60, 1000))
    }

    @Test
    fun serviceRejectsInvalidDurationAndPrice() {
        assertEquals("Тривалість має бути від 15 до 480 хв", validateService("Масаж", 0, 1000))
        assertEquals("Ціна має бути від 1 до 100 000 грн", validateService("Масаж", 60, 0))
    }

    @Test
    fun validServicePasses() {
        assertNull(validateService("Масаж спини", 45, 900))
    }

    @Test
    fun shiftRejectsMissingEmployeeAndInvalidTimes() {
        assertEquals("Оберіть майстра", validateShift(0, "09:00", "18:00"))
        assertEquals("Час має бути у форматі HH:MM", validateShift(1, "9:00", "18:00"))
        assertEquals("Час завершення має бути пізніше початку", validateShift(1, "18:00", "09:00"))
    }

    @Test
    fun validShiftPasses() {
        assertNull(validateShift(1, "09:00", "18:00"))
    }
}
