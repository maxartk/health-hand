package com.healthhand.admin.data

import com.healthhand.admin.data.models.AppointmentRequest
import com.healthhand.admin.data.models.AppointmentResponse
import com.healthhand.admin.data.models.BookingsResponse
import com.healthhand.admin.data.models.CatalogResponse
import com.healthhand.admin.data.models.EventsSummaryResponse
import com.healthhand.admin.data.models.StatusChangeRequest
import com.healthhand.admin.data.models.StatusChangeResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AdminApi {

    @GET("api/admin/bookings")
    suspend fun getBookings(@Query("status") status: String? = null): BookingsResponse

    @POST("api/admin/bookings/status")
    suspend fun changeBookingStatus(@Body request: StatusChangeRequest): StatusChangeResponse

    @POST("api/admin/appointments")
    suspend fun setAppointment(@Body request: AppointmentRequest): AppointmentResponse

    @GET("api/admin/v2/catalog")
    suspend fun getCatalog(): CatalogResponse

    @GET("api/admin/v2/events/summary")
    suspend fun getEventsSummary(@Query("days") days: Int = 7): EventsSummaryResponse
}