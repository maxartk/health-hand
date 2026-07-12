package com.healthhand.admin.core

import com.healthhand.admin.data.models.Booking
import com.healthhand.admin.data.models.CatalogResponse
import com.healthhand.admin.data.models.Employee
import com.healthhand.admin.data.models.EmployeeServiceLink
import com.healthhand.admin.data.models.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminLogicTest {
    @Test fun serviceFormRejectsBlankNameAndInvalidNumbers() {
        val errors = validateService(ServiceDraft(name = " ", description = "", duration = "0", price = "-1", sortOrder = "x"))
        assertEquals("Вкажіть назву послуги", errors.name)
        assertEquals("Тривалість має бути від 5 до 480 хв", errors.duration)
        assertEquals("Ціна не може бути від’ємною", errors.price)
        assertEquals("Вкажіть ціле число", errors.sortOrder)
        assertFalse(errors.isValid)
    }

    @Test fun validServiceDraftMapsToNormalizedRequestValues() {
        val draft = ServiceDraft("  Масаж спини  ", "  Опис  ", "45", "900", "20", categoryId = 3, active = true)
        assertTrue(validateService(draft).isValid)
        val request = draft.toRequest(id = 7)
        assertEquals("Масаж спини", request.name)
        assertEquals("Опис", request.description)
        assertEquals(45, request.duration_minutes)
        assertEquals(900, request.price)
        assertEquals(1, request.is_active)
    }

    @Test fun employeeFormRequiresNameAndAtLeastOneServiceWhenVisible() {
        val errors = validateEmployee(EmployeeDraft(name = "A", role = "", bio = "", phone = "", sortOrder = "0", active = true, visible = true, serviceIds = emptySet()))
        assertEquals("Вкажіть ім’я (мінімум 2 символи)", errors.name)
        assertEquals("Оберіть хоча б одну послугу", errors.services)
    }

    @Test fun catalogMapperSortsAndBuildsEmployeeAssignments() {
        val catalog = CatalogResponse(
            ok = true,
            services = listOf(Service(id=2,name="B",sort_order=20), Service(id=1,name="A",sort_order=10)),
            employees = listOf(Employee(id=5,name="Олена",sort_order=10)),
            employee_services = listOf(EmployeeServiceLink(employee_id=5, service_id=2))
        )
        val ui = catalog.toCatalogUi()
        assertEquals(listOf(1, 2), ui.services.map { it.id })
        assertEquals(setOf(2), ui.employees.single().serviceIds)
    }

    @Test fun reducerKeepsContentDuringRefreshAndSurfacesFailure() {
        val content = CatalogContent(listOf(Service(id=1,name="A")), emptyList(), emptyList())
        val refreshing = reduceCatalog(CatalogState(content = content), CatalogEvent.Refreshing)
        assertEquals(content, refreshing.content)
        assertTrue(refreshing.refreshing)
        val failed = reduceCatalog(refreshing, CatalogEvent.Failed("Немає мережі"))
        assertEquals(content, failed.content)
        assertFalse(failed.refreshing)
        assertEquals("Немає мережі", failed.error)
    }


    @Test fun shiftDraftValidatesClockRangeAndMapsBackendWeekday() {
        assertEquals("Час завершення має бути пізніше початку", validateShift(ShiftDraft(2, 0, "18:00", "09:00", true)).time)
        assertEquals("Вкажіть час у форматі ГГ:ХХ", validateShift(ShiftDraft(2, 0, "9:00", "18:00", true)).time)
        val request = ShiftDraft(2, 6, "09:00", "18:00", false).toRequest(9)
        assertEquals(6, request.weekday)
        assertEquals(0, request.is_active)
    }

    @Test fun bookingStatusesMatchCompleteBackendWorkflow() {
        val supported = setOf("new", "contacted", "confirmed", "completed", "cancelled", "no_show", "followup_sent")
        assertEquals(supported, editableBookingStatuses)
        supported.forEach { assertTrue(isEditableBookingStatus(it)) }
        assertFalse(isEditableBookingStatus("anything"))
    }

    @Test fun apiErrorMappingIsUsefulAndDoesNotExposeInternals() {
        assertEquals("Сеанс завершено. Увійдіть знову.", userMessageFor(403, null))
        assertEquals("Не вдалося з’єднатися. Перевірте інтернет.", userMessageFor(null, java.net.UnknownHostException()))
        assertNull(userMessageFor(200, null))
    }
}
