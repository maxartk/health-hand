package com.healthhand.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthhand.admin.core.*
import com.healthhand.admin.data.models.*
import com.healthhand.admin.ui.theme.HealthHandTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { HealthHandTheme(content = { AdminApp() }) }
    }
}
enum class Section(val title:String){CATALOG("Каталог"),TEAM("Команда"),BOOKINGS("Записи"),AUTOMATION("Авто"),STATS("Огляд")}
@Composable fun AdminApp(vm:AdminViewModel= viewModel()){val state by vm.state.collectAsStateWithLifecycle();val snackbar=remember{SnackbarHostState()};LaunchedEffect(vm){vm.events.collect{snackbar.showSnackbar(it.message)}};if(!state.authenticated){AccessScreen(state.checkingCredential,state.loginBusy,state.loginError,vm::signIn,vm::createPassword);return};var section by remember{mutableStateOf(Section.CATALOG)};Scaffold(snackbarHost={SnackbarHost(snackbar)},containerColor=MaterialTheme.colorScheme.background,bottomBar={NavigationBar{Section.entries.forEach{s->NavigationBarItem(selected=section==s,onClick={section=s;if(s==Section.BOOKINGS)vm.loadBookings();if(s==Section.AUTOMATION)vm.loadAutomation();if(s==Section.STATS)vm.loadStats()},icon={Icon(when(s){Section.CATALOG->Icons.Outlined.Spa;Section.TEAM->Icons.Outlined.Groups;Section.BOOKINGS->Icons.Outlined.CalendarMonth;Section.AUTOMATION->Icons.Outlined.AutoMode;Section.STATS->Icons.Outlined.Insights},null)},label={Text(s.title,maxLines=1)})}}}){pad->when(section){Section.CATALOG->CatalogScreen(state.catalog,state.busy,vm,Modifier.padding(pad));Section.TEAM->TeamScreen(state.catalog,state.busy,vm,Modifier.padding(pad));Section.BOOKINGS->BookingsScreen(state.bookings,state.bookingsLoading,state.busy,vm,Modifier.padding(pad));Section.AUTOMATION->AutomationScreen(state,vm,Modifier.padding(pad));Section.STATS->StatsScreen(state.stats,vm::logout,Modifier.padding(pad))}}}
@Composable fun AccessScreen(checking:Boolean,busy:Boolean,error:String?,onEnter:(String)->Unit,onCreatePassword:(String)->Unit){var password by remember{mutableStateOf("")};var createMode by rememberSaveable{mutableStateOf(false)};Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()){Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(24.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(24.dp)){Icon(Icons.Outlined.Spa,null,Modifier.padding(18.dp).size(36.dp),Color.White)};Spacer(Modifier.height(20.dp));Text("Health Hand",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Керування студією",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(32.dp));if(checking){CircularProgressIndicator();Spacer(Modifier.height(12.dp));Text("Перевіряємо збережений пароль…")}else{OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text(if(createMode)"Створіть пароль" else "Пароль доступу")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),leadingIcon={Icon(Icons.Outlined.Lock,null)},isError=error!=null,supportingText={Text(error?:if(createMode)"Мінімум 8 символів. Пароль зберігається лише на цьому пристрої." else "Пароль зберігається лише в захищеному сховищі цього пристрою")});Spacer(Modifier.height(16.dp));Button({if(createMode)onCreatePassword(password) else onEnter(password)},Modifier.fillMaxWidth().height(54.dp),enabled=password.length>=8&&!busy){if(busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp) else Text(if(createMode)"Створити пароль" else "Увійти")};TextButton({createMode = !createMode},enabled=!busy){Text(if(createMode)"У мене вже є пароль" else "Створити власний пароль")}}}}}
@Composable fun Header(title:String,subtitle:String,onRefresh:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)};onRefresh?.let{IconButton(it){Icon(Icons.Outlined.Refresh,"Оновити")}}}}
@Composable
fun CatalogScreen(state: CatalogState, busy: Boolean, vm: AdminViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf<Service?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Header("Каталог послуг", "Те, що бачать клієнти на сайті") { vm.loadCatalog(true) }
        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.content == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.content == null -> ErrorState(state.error ?: "Не вдалося завантажити каталог") { vm.loadCatalog() }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Категорії",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium) }
                    items(state.content.categories, key = { "c${it.id}" }) { category -> ElevatedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(category.name,fontWeight=FontWeight.SemiBold);if(category.description.isNotBlank())Text(category.description,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)};StatusBadge(category.is_active==1)}} }
                    item { Text("Послуги",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=10.dp)) }
                    items(state.content.services, key = { it.id }) { service -> ServiceCard(service, { editing = service }, { vm.deleteService(service.id) }) }
                    item { Spacer(Modifier.height(76.dp)) }
                }
            }
            ExtendedFloatingActionButton(text = { Text("Нова послуга") }, icon = { Icon(Icons.Outlined.Add, null) }, onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (adding || editing != null) ServiceDialog(editing, state.content?.categories.orEmpty(), busy, { adding = false; editing = null }) { draft ->
        vm.saveService(draft, editing?.id) { adding = false; editing = null }
    }

}
@Composable fun ServiceCard(s:Service,onEdit:()->Unit,onDelete:()->Unit){ElevatedCard(onClick=onEdit,Modifier.fillMaxWidth(),colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium);Text("${s.duration_minutes} хв  •  ${s.price} грн",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Medium)};StatusBadge(s.is_active==1)};if(s.description.isNotBlank()){Spacer(Modifier.height(8.dp));Text(s.description,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onDelete){Icon(Icons.Outlined.VisibilityOff,null);Spacer(Modifier.width(6.dp));Text("Приховати")};TextButton(onEdit){Text("Редагувати")}}}}}
@Composable fun StatusBadge(active:Boolean){Surface(color=if(active)Color(0xFFE1F4EA) else Color(0xFFF0F1EF),shape=RoundedCornerShape(99.dp)){Text(if(active)"На сайті" else "Приховано",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=if(active)Color(0xFF176B45) else Color(0xFF59615C),style=MaterialTheme.typography.labelMedium)}}
@Composable fun ServiceDialog(service:Service?,categories:List<Category>,busy:Boolean,dismiss:()->Unit,save:(ServiceDraft)->Unit){var name by remember{mutableStateOf(service?.name?:"")};var desc by remember{mutableStateOf(service?.description?:"")};var duration by remember{mutableStateOf((service?.duration_minutes?:60).toString())};var price by remember{mutableStateOf((service?.price?:0).toString())};var order by remember{mutableStateOf((service?.sort_order?:0).toString())};var categoryId by remember{mutableStateOf(service?.category_id)};var categoryMenu by remember{mutableStateOf(false)};var active by remember{mutableStateOf(service?.is_active!=0)};var attempted by remember{mutableStateOf(false)};val draft=ServiceDraft(name,desc,duration,price,order,categoryId,active);val errors=validateService(draft);AlertDialog(onDismissRequest={if(!busy)dismiss()},confirmButton={Button({attempted=true;if(errors.isValid)save(draft)},enabled=!busy){if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Text("Зберегти")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Скасувати")}},title={Text(if(service==null)"Нова послуга" else "Редагування послуги")},text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Field(name,{name=it},"Назва",attempted.then(errors.name));Box{OutlinedButton({categoryMenu=true},Modifier.fillMaxWidth()){Text(categories.firstOrNull{it.id==categoryId}?.name?:"Без категорії",Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(categoryMenu,{categoryMenu=false}){DropdownMenuItem({Text("Без категорії")},{categoryId=null;categoryMenu=false});categories.filter{it.is_active==1||it.id==categoryId}.forEach{c->DropdownMenuItem({Text(c.name)},{categoryId=c.id;categoryMenu=false})}}};Field(desc,{desc=it},"Опис",null,3);Field(duration,{duration=it},"Тривалість, хв",attempted.then(errors.duration),keyboard=true);Field(price,{price=it},"Ціна, грн",attempted.then(errors.price),keyboard=true);Field(order,{order=it},"Порядок на сайті",attempted.then(errors.sortOrder),keyboard=true);Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});Spacer(Modifier.width(8.dp));Text("Показувати на сайті")}}})}

private fun Boolean.then(value:String?)=if(this)value else null
@Composable fun Field(value:String,onChange:(String)->Unit,label:String,error:String?,lines:Int=1,keyboard:Boolean=false){OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},isError=error!=null,supportingText=error?.let{{Text(it)}},minLines=lines,maxLines=if(lines>1)5 else 1,keyboardOptions=if(keyboard)KeyboardOptions(keyboardType=KeyboardType.Number) else KeyboardOptions.Default)}
@Composable
fun TeamScreen(state: CatalogState, busy: Boolean, vm: AdminViewModel, modifier: Modifier) {
    var edit by remember { mutableStateOf<EmployeeUi?>(null) }
    var add by remember { mutableStateOf(false) }
    var editShift by remember { mutableStateOf<Shift?>(null) }
    var addShiftFor by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxSize()) {
        Header("Команда", "Працівники та прив’язані послуги") { vm.loadCatalog(true) }
        Box(Modifier.weight(1f)) {
            val content = state.content
            if (content == null) ErrorState(state.error ?: "Немає даних") { vm.loadCatalog() }
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(content.employees, key = { it.employee.id }) { item ->
                    ElevatedCard(onClick = { edit = item }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row { Column(Modifier.weight(1f)) { Text(item.employee.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); Text(if (item.employee.role == "massage_therapist") "Масажист" else item.employee.role, color = MaterialTheme.colorScheme.onSurfaceVariant) }; StatusBadge(item.employee.is_active == 1 && item.employee.show_on_site == 1) }
                            Spacer(Modifier.height(8.dp)); Text("Послуг: ${item.serviceIds.size}", color = MaterialTheme.colorScheme.primary)
                            content.shifts.filter{it.employee_id==item.employee.id}.forEach{shift->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${weekdayName(shift.weekday)}  ${shift.start_time}–${shift.end_time}",Modifier.weight(1f),color=if(shift.is_active==1)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant);IconButton({editShift=shift}){Icon(Icons.Outlined.Edit,"Редагувати зміну")};IconButton({vm.deleteShift(shift.id)}){Icon(Icons.Outlined.Delete,"Видалити зміну")}}}
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End){TextButton({addShiftFor=item.employee.id}){Icon(Icons.Outlined.Schedule,null);Text(" Додати зміну")};Row{TextButton({ vm.deleteEmployee(item.employee.id) }) { Text("Приховати") }; TextButton({ edit = item }) { Text("Редагувати") }}}
                        }
                    }
                }
                item { Spacer(Modifier.height(76.dp)) }
            }
            ExtendedFloatingActionButton(text = { Text("Додати") }, icon = { Icon(Icons.Outlined.PersonAdd, null) }, onClick = { add = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (add || edit != null) EmployeeDialog(edit, state.content, busy, { add = false; edit = null }) { draft -> vm.saveEmployee(draft, edit?.employee?.id) { add = false; edit = null } }
    if(addShiftFor!=null||editShift!=null)ShiftDialog(editShift,addShiftFor,state.content?.employees.orEmpty(),busy,{addShiftFor=null;editShift=null}){draft->vm.saveShift(draft,editShift?.id){addShiftFor=null;editShift=null}}
}
private fun weekdayName(day:Int)=listOf("Понеділок","Вівторок","Середа","Четвер","П’ятниця","Субота","Неділя").getOrElse(day){"День"}
@Composable fun EmployeeDialog(ui:EmployeeUi?,catalog:CatalogContent?,busy:Boolean,dismiss:()->Unit,save:(EmployeeDraft)->Unit){val e=ui?.employee;var name by remember{mutableStateOf(e?.name?:"")};var role by remember{mutableStateOf(e?.role?:"massage_therapist")};var bio by remember{mutableStateOf(e?.bio?:"")};var phone by remember{mutableStateOf(e?.phone?:"")};var order by remember{mutableStateOf((e?.sort_order?:0).toString())};var active by remember{mutableStateOf(e?.is_active!=0)};var visible by remember{mutableStateOf(e?.show_on_site!=0)};var selected by remember{mutableStateOf(ui?.serviceIds?:emptySet())};var attempted by remember{mutableStateOf(false)};val draft=EmployeeDraft(name,role,bio,phone,order,active,visible,selected);val errors=validateEmployee(draft);AlertDialog(onDismissRequest={if(!busy)dismiss()},confirmButton={Button({attempted=true;if(errors.isValid)save(draft)},enabled=!busy){Text("Зберегти")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Скасувати")}},title={Text(if(e==null)"Новий працівник" else "Редагування працівника")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Field(name,{name=it},"Ім’я",attempted.then(errors.name));Field(role,{role=it},"Роль",null);Field(phone,{phone=it},"Телефон",null);Field(bio,{bio=it},"Про спеціаліста",null,3);Field(order,{order=it},"Порядок",attempted.then(errors.sortOrder),keyboard=true);Text("Послуги",fontWeight=FontWeight.SemiBold);catalog?.services?.filter{it.is_active==1}?.forEach{s->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(s.id in selected,{checked->selected=if(checked)selected+s.id else selected-s.id});Text(s.name)}};if(attempted&&errors.services!=null)Text(errors.services!!,color=MaterialTheme.colorScheme.error);Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});Text(" Активний")};Row(verticalAlignment=Alignment.CenterVertically){Switch(visible,{visible=it});Text(" Показувати на сайті")}}})}
@Composable fun ShiftDialog(shift:Shift?,employeeId:Int?,employees:List<EmployeeUi>,busy:Boolean,dismiss:()->Unit,save:(ShiftDraft)->Unit){var selectedEmployee by remember{mutableStateOf(shift?.employee_id?:employeeId?:employees.firstOrNull()?.employee?.id?:0)};var day by remember{mutableIntStateOf(shift?.weekday?:0)};var start by remember{mutableStateOf(shift?.start_time?:"09:00")};var end by remember{mutableStateOf(shift?.end_time?:"18:00")};var active by remember{mutableStateOf(shift?.is_active!=0)};var empMenu by remember{mutableStateOf(false)};var dayMenu by remember{mutableStateOf(false)};var attempted by remember{mutableStateOf(false)};val draft=ShiftDraft(selectedEmployee,day,start,end,active);val errors=validateShift(draft);AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text(if(shift==null)"Нова зміна" else "Редагування зміни")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Box{OutlinedButton({empMenu=true},Modifier.fillMaxWidth()){Text(employees.firstOrNull{it.employee.id==selectedEmployee}?.employee?.name?:"Оберіть працівника",Modifier.weight(1f))};DropdownMenu(empMenu,{empMenu=false}){employees.forEach{e->DropdownMenuItem({Text(e.employee.name)},{selectedEmployee=e.employee.id;empMenu=false})}}};Box{OutlinedButton({dayMenu=true},Modifier.fillMaxWidth()){Text(weekdayName(day),Modifier.weight(1f))};DropdownMenu(dayMenu,{dayMenu=false}){(0..6).forEach{d->DropdownMenuItem({Text(weekdayName(d))},{day=d;dayMenu=false})}}};Field(start,{start=it},"Початок (ГГ:ХХ)",attempted.then(errors.time));Field(end,{end=it},"Завершення (ГГ:ХХ)",attempted.then(errors.time));Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});Text(" Доступний для запису")}}},confirmButton={Button({attempted=true;if(errors.isValid)save(draft)},enabled=!busy){Text("Зберегти")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Скасувати")}})}
@Composable
fun BookingsScreen(bookings: List<Booking>, loading: Boolean, busy: Boolean, vm: AdminViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf<Booking?>(null) }
    var deleting by remember { mutableStateOf<Booking?>(null) }
    Column(modifier.fillMaxSize()) {
        Header("Записи", "Статуси, нотатки та історія") { vm.loadBookings() }
        when {
            loading && bookings.isEmpty() -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            bookings.isEmpty() -> EmptyState("Записів поки немає")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(bookings, key = { it.id }) { booking ->
                    ElevatedCard(onClick = { editing = booking }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row { Column(Modifier.weight(1f)) { Text(booking.lead_name.ifBlank { "Клієнт" }, fontWeight = FontWeight.Bold); Text(booking.service, color = MaterialTheme.colorScheme.primary) }; Text(statusLabel(booking.status), fontWeight = FontWeight.SemiBold) }
                            Text(listOf(booking.date, booking.time, booking.lead_contact).filter { it.isNotBlank() }.joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (booking.note.isNotBlank()) Text(booking.note, Modifier.padding(top = 8.dp))
                            if (booking.feedback_note.isNotBlank()) Text("Відгук: ${booking.feedback_note}", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ deleting = booking }) { Icon(Icons.Outlined.Delete, null); Text(" Видалити") }; TextButton({ editing = booking }) { Text("Редагувати") } }
                        }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    editing?.let { booking -> BookingDialog(booking, busy, { editing = null }) { status, note -> vm.updateBooking(booking.id, status, note) { editing = null } } }
    deleting?.let { booking -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Видалити запис?") }, text = { Text("Запис клієнта «${booking.lead_name.ifBlank { "Клієнт" }}» буде видалено безповоротно.") }, confirmButton = { Button({ vm.deleteBooking(booking.id) { deleting = null } }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Видалити") } }, dismissButton = { TextButton({ deleting = null }, enabled = !busy) { Text("Скасувати") } }) }
}
private fun statusLabel(status:String)=mapOf("new" to "Новий","contacted" to "Зв’язалися","confirmed" to "Підтверджено","completed" to "Завершено","cancelled" to "Скасовано","no_show" to "Не прийшов","followup_sent" to "Надіслано нагадування")[status]?:status
@Composable fun BookingDialog(booking:Booking,busy:Boolean,dismiss:()->Unit,save:(String,String)->Unit){var status by remember{mutableStateOf(if(isEditableBookingStatus(booking.status))booking.status else "new")};var note by remember{mutableStateOf(booking.feedback_note)};var menu by remember{mutableStateOf(false)};AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Редагування запису")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(booking.lead_name.ifBlank{"Клієнт"},fontWeight=FontWeight.Bold);Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(statusLabel(status),Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(menu,{menu=false}){editableBookingStatuses.forEach{s->DropdownMenuItem({Text(statusLabel(s))},{status=s;menu=false})}}};Field(note,{note=it},"Нотатка після візиту",null,3)}},confirmButton={Button({save(status,note)},enabled=!busy){Text("Зберегти")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Скасувати")}})}
@Composable
fun AutomationScreen(state:AppState,vm:AdminViewModel,modifier:Modifier){
    val summary=state.automationSummary
    Column(modifier.fillMaxSize()){
        Header("Автоматизація","Android → backend → n8n"){vm.loadAutomation()}
        if(state.automationLoading&&summary==null){Box(Modifier.fillMaxSize()){CircularProgressIndicator(Modifier.align(Alignment.Center))};return@Column}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(if(summary?.configured==true)Icons.Outlined.CheckCircle else Icons.Outlined.Warning,null,tint=if(summary?.configured==true)Color(0xFF176B45) else MaterialTheme.colorScheme.error);Spacer(Modifier.width(10.dp));Column{Text(if(summary?.configured==true)"n8n підключено" else "n8n не підключено",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(if(summary?.configured==true)"Події передаються через захищений backend" else "Автоматизація очікує налаштування на сервері",color=MaterialTheme.colorScheme.onSurfaceVariant)}};Spacer(Modifier.height(14.dp));Button(vm::testAutomation,Modifier.fillMaxWidth(),enabled=!state.busy&&summary?.configured==true){Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Перевірити автоматизацію")}}}}
            summary?.let{s->item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){AutomationMetric("Очікують",s.counts.pending,Color(0xFFB26A00),Modifier.weight(1f));AutomationMetric("Доставлено",s.counts.delivered,Color(0xFF176B45),Modifier.weight(1f));AutomationMetric("Помилки",s.counts.failed,MaterialTheme.colorScheme.error,Modifier.weight(1f))}};item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Остання активність",fontWeight=FontWeight.Bold);Text("Успішна доставка: ${formatAutomationTime(s.last_success_at)}");Text("Остання помилка: ${formatAutomationTime(s.last_failure_at)}",color=if(s.last_failure_at!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}}}}
            item{Text("Останні події",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)}
            if(state.automationEvents.isEmpty())item{EmptyState("Подій автоматизації поки немає")}
            items(state.automationEvents,key={it.event_id}){event->AutomationEventCard(event,state.busy){vm.retryAutomation(event.event_id)}}
            item{Spacer(Modifier.height(8.dp))}
        }
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
@Composable private fun AutomationMetric(label:String,value:Int,color:Color,modifier:Modifier){Surface(modifier,color=color.copy(alpha=.1f),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(horizontal=10.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(value.toString(),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall,color=color);Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1)}}}
@Composable private fun AutomationEventCard(event:AutomationEvent,busy:Boolean,retry:()->Unit){val color=when(event.status){"delivered"->Color(0xFF176B45);"failed"->MaterialTheme.colorScheme.error;else->Color(0xFFB26A00)};ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(automationEventLabel(event.event_name),fontWeight=FontWeight.SemiBold);if(event.summary.isNotBlank())Text(event.summary,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium);Text(formatAutomationTime(event.created_at),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Surface(color=color.copy(alpha=.1f),shape=RoundedCornerShape(99.dp)){Text(automationStatusLabel(event.status),Modifier.padding(horizontal=9.dp,vertical=4.dp),color=color,style=MaterialTheme.typography.labelMedium)}};if(event.attempts>1)Text("Спроб доставки: ${event.attempts}",Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);if(event.status=="failed"){Text(automationErrorLabel(event.last_error),Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.error);OutlinedButton(retry,Modifier.fillMaxWidth().padding(top=8.dp),enabled=!busy){Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text("Повторити доставку")}}}}}
private fun automationStatusLabel(v:String)=when(v){"delivered"->"Доставлено";"failed"->"Помилка";else->"Очікує"}
private fun automationEventLabel(v:String)=mapOf("automation_test" to "Перевірка автоматизації","service_created" to "Створено послугу","service_updated" to "Оновлено послугу","service_disabled" to "Приховано послугу","employee_created" to "Додано працівника","employee_updated" to "Оновлено працівника","employee_disabled" to "Приховано працівника","shift_created" to "Додано графік","shift_updated" to "Оновлено графік","shift_deleted" to "Видалено графік","booking_created" to "Новий запис","booking_status_changed" to "Змінено статус запису","booking_deleted" to "Видалено запис","user_registered" to "Новий клієнт")[v]?:"Подія автоматизації"
private fun automationErrorLabel(v:String?)=when(v){"not_configured"->"n8n ще не налаштовано на сервері";"network_error"->"Сервер автоматизації тимчасово недоступний";else->"Не вдалося доставити подію"}
private fun formatAutomationTime(value:Long?):String{if(value==null||value<=0)return "ще немає";return java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale("uk","UA")).format(java.util.Date(value*1000))}

@Composable fun StatsScreen(stats:EventsSummaryResponse?,logout:()->Unit,modifier:Modifier){Column(modifier.fillMaxSize()){Header("Огляд","Активність сайту за 7 днів");LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){if(stats==null)item{EmptyState("Оновлення статистики…")}else{items(stats.events.toList().sortedByDescending{it.second}){(name,count)->ElevatedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(eventLabel(name),Modifier.weight(1f));Text(count.toString(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}}};if(stats.top_services.isNotEmpty())item{Text("Популярні послуги",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))};items(stats.top_services){s->ListItem(headlineContent={Text(s.service)},trailingContent={Text(s.count.toString(),fontWeight=FontWeight.Bold)})}};item{OutlinedButton(logout,Modifier.fillMaxWidth().padding(top=20.dp)){Icon(Icons.AutoMirrored.Outlined.Logout,null);Spacer(Modifier.width(8.dp));Text("Вийти з адмін-панелі")}}}}}
private fun eventLabel(v:String)=mapOf("page_view" to "Перегляди сторінок","service_selected" to "Вибір послуги","booking_submitted" to "Надіслані заявки")[v]?:v.replace('_',' ')
@Composable fun ErrorState(text:String,retry:()->Unit){Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CloudOff,null,Modifier.size(42.dp),MaterialTheme.colorScheme.error);Spacer(Modifier.height(12.dp));Text(text);TextButton(retry){Text("Спробувати знову")}}}
@Composable fun EmptyState(text:String){Column(Modifier.fillMaxWidth().padding(36.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Spa,null,Modifier.size(38.dp),MaterialTheme.colorScheme.primary);Spacer(Modifier.height(10.dp));Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
