package com.healthhand.admin.data.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) data class CatalogResponse(val ok:Boolean=false,val error:String?=null,val categories:List<Category> = emptyList(),val services:List<Service> = emptyList(),val employees:List<Employee> = emptyList(),val shifts:List<Shift> = emptyList(),val employee_services:List<EmployeeServiceLink> = emptyList())
@JsonClass(generateAdapter = true) data class Category(val id:Int=0,val name:String="",val description:String="",val sort_order:Int=0,val is_active:Int=1,val created_at:Int=0)
@JsonClass(generateAdapter = true) data class Service(val id:Int=0,val name:String="",val description:String="",val duration_minutes:Int=60,val price:Int=0,val category_id:Int?=null,val sort_order:Int=0,val is_active:Int=1,val created_at:Int=0)
@JsonClass(generateAdapter = true) data class Employee(val id:Int=0,val name:String="",val role:String="massage_therapist",val bio:String="",val phone:String="",val is_active:Int=1,val sort_order:Int=0,val show_on_site:Int=1,val created_at:Int=0)
@JsonClass(generateAdapter = true) data class Shift(val id:Int=0,val employee_id:Int=0,val weekday:Int=0,val start_time:String="",val end_time:String="",val is_active:Int=1,val created_at:Int=0)
@JsonClass(generateAdapter = true) data class EmployeeServiceLink(val employee_id:Int=0,val service_id:Int=0)
@JsonClass(generateAdapter = true) data class ServiceRequest(val id:Int?=null,val name:String,val description:String="",val duration_minutes:Int=60,val price:Int=0,val category_id:Int?=null,val sort_order:Int=0,val is_active:Int=1)
@JsonClass(generateAdapter = true) data class ServiceResponse(val ok:Boolean=false,val error:String?=null,val service:Service?=null)

@JsonClass(generateAdapter = true) data class EmployeeRequest(val id:Int?=null,val name:String,val role:String="massage_therapist",val bio:String="",val phone:String="",val is_active:Int=1,val sort_order:Int=0,val show_on_site:Int=1,val service_ids:List<Int> = emptyList())
@JsonClass(generateAdapter = true) data class EmployeeResponse(val ok:Boolean=false,val error:String?=null,val employee:Employee?=null)
@JsonClass(generateAdapter = true) data class ShiftRequest(val id:Int?=null,val employee_id:Int,val weekday:Int,val start_time:String,val end_time:String,val is_active:Int=1)
@JsonClass(generateAdapter = true) data class ShiftResponse(val ok:Boolean=false,val error:String?=null,val shift:Shift?=null)
@JsonClass(generateAdapter = true) data class DeleteResponse(val ok:Boolean=false,val deleted:Int?=null,val hard:Boolean?=null,val error:String?=null)
@JsonClass(generateAdapter = true) data class BookingsResponse(val ok:Boolean=false,val error:String?=null,val bookings:List<Booking> = emptyList())
@JsonClass(generateAdapter = true) data class Booking(val id:Int=0,val user_id:Int?=null,val lead_name:String="",val lead_contact:String="",val service:String="",val service_id:Int?=null,val employee_id:Int?=null,val start_at:String="",val end_at:String="",val date:String="",val time:String="",val note:String="",val feedback_note:String="",val channel:String="",val status:String="new",val created_at:Int=0)
@JsonClass(generateAdapter = true) data class StatusChangeRequest(val booking_id:Int,val status:String,val feedback_note:String?=null)
@JsonClass(generateAdapter = true) data class StatusChangeResponse(val ok:Boolean=false,val error:String?=null,val booking:Booking?=null)
@JsonClass(generateAdapter = true) data class EventsSummaryResponse(val ok:Boolean=false,val error:String?=null,val days:Int=7,val events:Map<String,Int> = emptyMap(),val top_services:List<TopService> = emptyList())
@JsonClass(generateAdapter = true) data class TopService(val service:String="",val count:Int=0)
