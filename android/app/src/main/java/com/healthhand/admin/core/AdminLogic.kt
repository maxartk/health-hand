package com.healthhand.admin.core

import com.healthhand.admin.data.models.*
import java.net.UnknownHostException

data class ServiceDraft(val name:String,val description:String,val duration:String,val price:String,val sortOrder:String,val categoryId:Int?=null,val active:Boolean=true)
data class ServiceErrors(val name:String?=null,val duration:String?=null,val price:String?=null,val sortOrder:String?=null){ val isValid get()=listOf(name,duration,price,sortOrder).all{it==null} }
fun validateService(d:ServiceDraft):ServiceErrors { val duration=d.duration.toIntOrNull(); val price=d.price.toIntOrNull(); return ServiceErrors(if(d.name.trim().length<2)"Вкажіть назву послуги" else null,if(duration==null||duration !in 5..480)"Тривалість має бути від 5 до 480 хв" else null,if(price==null||price<0)"Ціна не може бути від’ємною" else null,if(d.sortOrder.toIntOrNull()==null)"Вкажіть ціле число" else null) }
fun ServiceDraft.toRequest(id:Int?=null)=ServiceRequest(id,name.trim(),description.trim(),duration.toInt(),price.toInt(),categoryId,sortOrder.toInt(),if(active)1 else 0)

data class ShiftDraft(val employeeId:Int,val weekday:Int,val startTime:String,val endTime:String,val active:Boolean)
data class ShiftErrors(val employee:String?=null,val weekday:String?=null,val time:String?=null){val isValid get()=employee==null&&weekday==null&&time==null}
fun validateShift(d:ShiftDraft):ShiftErrors { val clock=Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$"); return ShiftErrors(if(d.employeeId<=0)"Оберіть працівника" else null,if(d.weekday !in 0..6)"Оберіть день" else null,when{!clock.matches(d.startTime)||!clock.matches(d.endTime)->"Вкажіть час у форматі ГГ:ХХ";d.endTime<=d.startTime->"Час завершення має бути пізніше початку";else->null}) }
fun ShiftDraft.toRequest(id:Int?=null)=ShiftRequest(id,employeeId,weekday,startTime,endTime,if(active)1 else 0)
val editableBookingStatuses=setOf("new","contacted","confirmed","completed","cancelled","no_show","followup_sent")
fun isEditableBookingStatus(status:String)=status in editableBookingStatuses
data class EmployeeDraft(val name:String,val role:String,val bio:String,val phone:String,val sortOrder:String,val active:Boolean,val visible:Boolean,val serviceIds:Set<Int>)
data class EmployeeErrors(val name:String?=null,val sortOrder:String?=null,val services:String?=null){val isValid get()=listOf(name,sortOrder,services).all{it==null}}
fun validateEmployee(d:EmployeeDraft)=EmployeeErrors(if(d.name.trim().length<2)"Вкажіть ім’я (мінімум 2 символи)" else null,if(d.sortOrder.toIntOrNull()==null)"Вкажіть ціле число" else null,if(d.visible&&d.serviceIds.isEmpty())"Оберіть хоча б одну послугу" else null)
fun EmployeeDraft.toRequest(id:Int?=null)=EmployeeRequest(id,name.trim(),role.trim().ifBlank{"massage_therapist"},bio.trim(),phone.trim(),if(active)1 else 0,sortOrder.toInt(),if(visible)1 else 0,serviceIds.sorted())
data class EmployeeUi(val employee:Employee,val serviceIds:Set<Int>)
data class CatalogContent(val services:List<Service>,val employees:List<EmployeeUi>,val categories:List<Category>,val shifts:List<Shift> = emptyList())
fun CatalogResponse.toCatalogUi()=CatalogContent(services.sortedWith(compareBy<Service>{it.sort_order}.thenBy{it.id}),employees.sortedWith(compareBy<Employee>{it.sort_order}.thenBy{it.id}).map{e->EmployeeUi(e,employee_services.filter{it.employee_id==e.id}.map{it.service_id}.toSet())},categories.sortedWith(compareBy<Category>{it.sort_order}.thenBy{it.id}),shifts.sortedWith(compareBy<Shift>{it.employee_id}.thenBy{it.weekday}.thenBy{it.start_time}))
data class CatalogState(val loading:Boolean=false,val refreshing:Boolean=false,val content:CatalogContent?=null,val error:String?=null,val notice:String?=null)
sealed interface CatalogEvent { data object Loading:CatalogEvent; data object Refreshing:CatalogEvent; data class Loaded(val content:CatalogContent):CatalogEvent; data class Failed(val message:String):CatalogEvent; data class Notice(val message:String):CatalogEvent }
fun reduceCatalog(s:CatalogState,e:CatalogEvent)=when(e){CatalogEvent.Loading->s.copy(loading=true,error=null);CatalogEvent.Refreshing->s.copy(refreshing=true,error=null);is CatalogEvent.Loaded->s.copy(loading=false,refreshing=false,content=e.content,error=null);is CatalogEvent.Failed->s.copy(loading=false,refreshing=false,error=e.message);is CatalogEvent.Notice->s.copy(notice=e.message)}
fun userMessageFor(code:Int?,cause:Throwable?):String?=when{code==403->"Сеанс завершено. Увійдіть знову.";cause is UnknownHostException->"Не вдалося з’єднатися. Перевірте інтернет.";code!=null&&code in 200..299->null;else->"Не вдалося виконати дію. Спробуйте ще раз."}
