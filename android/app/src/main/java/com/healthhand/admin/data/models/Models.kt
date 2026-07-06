package com.healthhand.admin.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/* ----- Generic response wrapper ----- */

@JsonClass(generateAdapter = true)
data class BaseResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

/* ----- Bookings (GET /api/admin/bookings) ----- */

@JsonClass(generateAdapter = true)
data class BookingsResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val bookings: List<Booking> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Booking(
    val id: Int = 0,
    val user_id: Int? = null,
    val external_id: String? = null,
    val lead_name: String = "",
    val lead_contact: String = "",
    val service: String = "",
    val service_id: Int? = null,
    val employee_id: Int? = null,
    val start_at: String = "",
    val end_at: String = "",
    val date: String = "",
    val time: String = "",
    val note: String = "",
    val feedback_note: String = "",
    val channel: String = "",
    val status: String = "new",
    val webhook_ok: Int = 0,
    val created_at: Int = 0,
)

/* ----- Status change (POST /api/admin/bookings/status) ----- */

@JsonClass(generateAdapter = true)
data class StatusChangeRequest(
    @Json(name = "booking_id") val booking_id: Int,
    @Json(name = "status") val status: String,
    @Json(name = "feedback_note") val feedback_note: String? = null,
)

@JsonClass(generateAdapter = true)
data class StatusChangeResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val booking: Booking? = null,
)

/* ----- Appointment (POST /api/admin/appointments) ----- */

@JsonClass(generateAdapter = true)
data class AppointmentRequest(
    @Json(name = "booking_id") val booking_id: Int,
    @Json(name = "start_at") val start_at: String,
    @Json(name = "end_at") val end_at: String? = null,
    @Json(name = "employee_id") val employee_id: Int? = null,
    @Json(name = "status") val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class AppointmentResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val appointment: Appointment? = null,
)

@JsonClass(generateAdapter = true)
data class Appointment(
    val id: Int = 0,
    val booking_id: Int = 0,
    val user_id: Int? = null,
    val employee_id: Int? = null,
    val start_at: String = "",
    val end_at: String = "",
    val calendar_event_id: String = "",
    val status: String = "scheduled",
    val created_at: Int = 0,
)

/* ----- Catalog (GET /api/admin/v2/catalog) ----- */

@JsonClass(generateAdapter = true)
data class CatalogResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val services: List<Service> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val shifts: List<Shift> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Category(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val sort_order: Int = 0,
    val is_active: Int = 1,
    val created_at: Int = 0,
)

@JsonClass(generateAdapter = true)
data class Service(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val duration_minutes: Int = 60,
    val price: Int = 0,
    val category_id: Int? = null,
    val sort_order: Int = 0,
    val is_active: Int = 1,
    val created_at: Int = 0,
)

@JsonClass(generateAdapter = true)
data class Employee(
    val id: Int = 0,
    val name: String = "",
    val role: String = "massage_therapist",
    val bio: String = "",
    val phone: String = "",
    val is_active: Int = 1,
    val sort_order: Int = 0,
    val show_on_site: Int = 1,
    val created_at: Int = 0,
)

@JsonClass(generateAdapter = true)
data class Shift(
    val id: Int = 0,
    val employee_id: Int = 0,
    val weekday: Int = 0,
    val start_time: String = "",
    val end_time: String = "",
    val is_active: Int = 1,
    val created_at: Int = 0,
)

/* ----- Events summary (GET /api/admin/v2/events/summary) ----- */

@JsonClass(generateAdapter = true)
data class EventsSummaryResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val days: Int = 7,
    val events: Map<String, Int> = emptyMap(),
    val top_services: List<TopService> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TopService(
    val service: String = "",
    val count: Int = 0,
)

/* ----- Service CRUD ----- */

@JsonClass(generateAdapter = true)
data class ServiceRequest(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String = "",
    @Json(name = "duration_minutes") val duration_minutes: Int = 60,
    @Json(name = "price") val price: Int = 0,
    @Json(name = "category_id") val category_id: Int? = null,
    @Json(name = "sort_order") val sort_order: Int = 0,
    @Json(name = "is_active") val is_active: Int = 1,
)

@JsonClass(generateAdapter = true)
data class ServiceResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val service: Service? = null,
)

/* ----- Employee CRUD ----- */

@JsonClass(generateAdapter = true)
data class EmployeeRequest(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String,
    @Json(name = "role") val role: String = "massage_therapist",
    @Json(name = "bio") val bio: String = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "is_active") val is_active: Int = 1,
    @Json(name = "sort_order") val sort_order: Int = 0,
    @Json(name = "show_on_site") val show_on_site: Int = 1,
    @Json(name = "service_ids") val service_ids: List<Int>? = null,
)

@JsonClass(generateAdapter = true)
data class EmployeeResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val employee: Employee? = null,
)

/* ----- Shift CRUD ----- */

@JsonClass(generateAdapter = true)
data class ShiftRequest(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "employee_id") val employee_id: Int,
    @Json(name = "weekday") val weekday: Int,
    @Json(name = "start_time") val start_time: String,
    @Json(name = "end_time") val end_time: String,
    @Json(name = "is_active") val is_active: Int = 1,
)

@JsonClass(generateAdapter = true)
data class ShiftResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val shift: Shift? = null,
)

/* ----- Generic delete response ----- */

@JsonClass(generateAdapter = true)
data class DeleteResponse(
    val ok: Boolean = false,
    val deleted: Int? = null,
    val hard: Boolean? = null,
    val error: String? = null,
)