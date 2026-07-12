package com.healthhand.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.healthhand.admin.BuildConfig
import com.healthhand.admin.data.models.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface AdminApi {
 @GET("api/admin/v2/catalog") suspend fun catalog():CatalogResponse
 @POST("api/admin/v2/services") suspend fun createService(@Body r:ServiceRequest):ServiceResponse
 @PUT("api/admin/v2/services") suspend fun updateService(@Body r:ServiceRequest):ServiceResponse
 @DELETE("api/admin/v2/services") suspend fun deleteService(@Query("id") id:Int):DeleteResponse

 @POST("api/admin/v2/employees") suspend fun createEmployee(@Body r:EmployeeRequest):EmployeeResponse
 @PUT("api/admin/v2/employees") suspend fun updateEmployee(@Body r:EmployeeRequest):EmployeeResponse
 @DELETE("api/admin/v2/employees") suspend fun deleteEmployee(@Query("id") id:Int):DeleteResponse
 @POST("api/admin/v2/shifts") suspend fun createShift(@Body r:ShiftRequest):ShiftResponse
 @PUT("api/admin/v2/shifts") suspend fun updateShift(@Body r:ShiftRequest):ShiftResponse
 @DELETE("api/admin/v2/shifts") suspend fun deleteShift(@Query("id") id:Int):DeleteResponse
 @GET("api/admin/bookings") suspend fun bookings(@Query("status") status:String?=null):BookingsResponse
 @POST("api/admin/bookings/status") suspend fun status(@Body r:StatusChangeRequest):StatusChangeResponse
 @DELETE("api/admin/bookings") suspend fun deleteBooking(@Query("id") id:Int):DeleteResponse
 @GET("api/admin/v2/events/summary") suspend fun stats(@Query("days") days:Int=7):EventsSummaryResponse
}

class SecureCredentialStore(context:Context){
 private val prefs=EncryptedSharedPreferences.create(context,"health_hand_admin_v2",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 fun token()=prefs.getString("credential",null)
 fun save(value:String){require(value.isNotBlank());prefs.edit().putString("credential",value.trim()).apply()}
 fun clear()=prefs.edit().remove("credential").apply()
}

class AdminRepository(private val store:SecureCredentialStore){
 companion object { const val TRUSTED_HOST="healthhand.duckdns.org" }
 private val api:AdminApi
 init {
  val logging=HttpLoggingInterceptor().apply{redactHeader("Authorization");level=if(BuildConfig.DEBUG)HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE}
  val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).addInterceptor{chain->
   check(chain.request().url.host==TRUSTED_HOST){"Недозволений сервер"}
   val token=store.token() ?: ""
   chain.proceed(chain.request().newBuilder().header("Authorization","Bearer $token").build())
  }.addInterceptor(logging).build()
  val moshi=Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  api=Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client).addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(AdminApi::class.java)
 }
 fun hasCredential()=!store.token().isNullOrBlank()
 fun saveCredential(v:String)=store.save(v)
 fun logout()=store.clear()
 suspend fun catalog()=api.catalog().requireOk()
 suspend fun save(r:ServiceRequest)=(if(r.id==null)api.createService(r) else api.updateService(r)).requireOk()
 suspend fun deleteService(id:Int)=api.deleteService(id).requireOk()

 suspend fun save(r:EmployeeRequest)=(if(r.id==null)api.createEmployee(r) else api.updateEmployee(r)).requireOk()
 suspend fun deleteEmployee(id:Int)=api.deleteEmployee(id).requireOk()
 suspend fun save(r:ShiftRequest)=(if(r.id==null)api.createShift(r) else api.updateShift(r)).requireOk()
 suspend fun deleteShift(id:Int)=api.deleteShift(id).requireOk()
 suspend fun bookings()=api.bookings().requireOk()
 suspend fun status(id:Int,status:String,note:String)=api.status(StatusChangeRequest(id,status,note)).requireOk()
 suspend fun deleteBooking(id:Int)=api.deleteBooking(id).requireOk()
 suspend fun stats()=api.stats().requireOk()
}
private fun apiFailure(error:String?):Nothing=throw IllegalStateException(error?:"Сервер відхилив дію")
private fun CatalogResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun ServiceResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}

private fun EmployeeResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun ShiftResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun DeleteResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun BookingsResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun StatusChangeResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
private fun EventsSummaryResponse.requireOk()=also{if(!it.ok)apiFailure(it.error)}
fun Throwable.httpCode()=(this as? HttpException)?.code()
