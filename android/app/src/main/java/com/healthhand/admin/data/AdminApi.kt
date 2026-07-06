package com.healthhand.admin.data

import com.healthhand.admin.data.models.AppointmentRequest
import com.healthhand.admin.data.models.AppointmentResponse
import com.healthhand.admin.data.models.BookingsResponse
import com.healthhand.admin.data.models.CatalogResponse
import com.healthhand.admin.data.models.DeleteResponse
import com.healthhand.admin.data.models.EmployeeRequest
import com.healthhand.admin.data.models.EmployeeResponse
import com.healthhand.admin.data.models.EventsSummaryResponse
import com.healthhand.admin.data.models.ServiceRequest
import com.healthhand.admin.data.models.ServiceResponse
import com.healthhand.admin.data.models.ShiftRequest
import com.healthhand.admin.data.models.ShiftResponse
import com.healthhand.admin.data.models.StatusChangeRequest
import com.healthhand.admin.data.models.StatusChangeResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface AdminApi {

    @GET("api/admin/bookings")
    suspend fun getBookings(@Query("status") status: String? = null): BookingsResponse

    @POST("api/admin/bookings/status")
    suspend fun changeBookingStatus(@Body request: StatusChangeRequest): StatusChangeResponse

    @POST("api/admin/appointments")
    suspend fun setAppointment(@Body request: AppointmentRequest): AppointmentResponse

    @DELETE("api/admin/bookings")
    suspend fun deleteBooking(@Query("id") id: Int): DeleteResponse

    @GET("api/admin/v2/catalog")
    suspend fun getCatalog(): CatalogResponse

    @GET("api/admin/v2/events/summary")
    suspend fun getEventsSummary(@Query("days") days: Int = 7): EventsSummaryResponse

    /* ----- Services ----- */

    @POST("api/admin/v2/services")
    suspend fun createService(@Body request: ServiceRequest): ServiceResponse

    @PUT("api/admin/v2/services")
    suspend fun updateService(@Body request: ServiceRequest): ServiceResponse

    @DELETE("api/admin/v2/services")
    suspend fun deleteService(@Query("id") id: Int): DeleteResponse

    /* ----- Employees ----- */

    @POST("api/admin/v2/employees")
    suspend fun createEmployee(@Body request: EmployeeRequest): EmployeeResponse

    @PUT("api/admin/v2/employees")
    suspend fun updateEmployee(@Body request: EmployeeRequest): EmployeeResponse

    @DELETE("api/admin/v2/employees")
    suspend fun deleteEmployee(@Query("id") id: Int): DeleteResponse

    /* ----- Shifts ----- */

    @POST("api/admin/v2/shifts")
    suspend fun createShift(@Body request: ShiftRequest): ShiftResponse

    @PUT("api/admin/v2/shifts")
    suspend fun updateShift(@Body request: ShiftRequest): ShiftResponse

    @DELETE("api/admin/v2/shifts")
    suspend fun deleteShift(@Query("id") id: Int): DeleteResponse
}