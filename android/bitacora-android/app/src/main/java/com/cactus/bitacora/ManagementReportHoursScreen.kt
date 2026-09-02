package com.cactus.bitacora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class ManagementBreakdown { TIME, AREA }
private data class PeriodChoice(val type:String,val code:String,val path:String,val from:LocalDate,val to:LocalDate)
private data class BarValues(val total:Int,val light:Int,val dark:Int,val extra:Int,val special:Int)

@Composable
internal fun ManagementReportHoursScreen(repository: BitacoraRepository, session: ManagementSessionOut,
                                         onBack: () -> Unit) {
    val scope=rememberCoroutineScope(); val zone=java.time.ZoneId.of("America/Bogota")
    val today=LocalDate.now(zone); val initial=PeriodChoice("ANIO",today.year.toString(),today.year.toString(),today.withDayOfYear(1),today.withMonth(12).withDayOfMonth(31))
    val periodSaver=listSaver<PeriodChoice,String>(
        save={listOf(it.type,it.code,it.path,it.from.toString(),it.to.toString())},
        restore={PeriodChoice(it[0],it[1],it[2],LocalDate.parse(it[3]),LocalDate.parse(it[4]))})
    var period by rememberSaveable(stateSaver=periodSaver) { mutableStateOf(initial) }
    var areaId by rememberSaveable { mutableStateOf<Int?>(null) }
    var areaPath by rememberSaveable { mutableStateOf("Organización") }
    var breakdown by rememberSaveable { mutableStateOf(ManagementBreakdown.TIME) }
    var report by remember { mutableStateOf<ManagementAggregateReportOut?>(null) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var expandedTime by rememberSaveable { mutableStateOf(listOf<String>()) }
    var expandedAreas by rememberSaveable { mutableStateOf(listOf<Int>()) }
    fun load(choice:PeriodChoice=period, selectedArea:Int?=areaId)=scope.launch {
        loading=true; error=null
        runCatching { repository.managementAggregateReport(session,choice.from,choice.to,selectedArea) }
            .onSuccess { report=it }.onFailure { error=it.message ?: "No fue posible cargar el informe" }
        loading=false
    }
    fun chooseTime(node:ManagementTimeNodeOut) {
        val choice=periodChoice(node); period=choice; load(choice,areaId)
    }
    fun chooseArea(node:ManagementAreaAggregateOut) { areaId=node.id_area;areaPath=node.ruta;load(period,node.id_area) }
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Informe gerencial de horas laboradas",style=MaterialTheme.typography.headlineSmall)
        Text("Tiempo: ${period.path}"); Text("Área: $areaPath")
        if(period.type!="ANIO") OutlinedButton({period=initial;load(initial,areaId)}){Text("Regresar al año")}
        if(areaId!=null) OutlinedButton({areaId=null;areaPath="Organización";load(period,null)}){Text("Regresar a Organización")}
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        report?.let { data ->
            val max=maxVisible(data)
            Text("Árbol de tiempo",style=MaterialTheme.typography.titleLarge)
            data.tiempo.filter{it.total_minutos>0}.forEach { TimeNode(it,0,max,period.code,expandedTime,
                { expandedTime=toggle(expandedTime,it) },::chooseTime) }
            Text("Árbol de áreas administrativas",style=MaterialTheme.typography.titleLarge)
            data.organizacion.filter{it.total_minutos>0}.forEach { AreaNode(it,0,max,areaId,expandedAreas,
                { expandedAreas=toggle(expandedAreas,it) },::chooseArea) }
            HorizontalDivider(); Text("Selección: ${period.path} × $areaPath",style=MaterialTheme.typography.titleMedium)
            Indicators(data.resumen,data.generado_en)
            ReportLegend()
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(breakdown==ManagementBreakdown.TIME,{breakdown=ManagementBreakdown.TIME},{Text("Desglosar por tiempo")})
                FilterChip(breakdown==ManagementBreakdown.AREA,{breakdown=ManagementBreakdown.AREA},{Text("Desglosar por área")})
            }
            val rows=if(breakdown==ManagementBreakdown.TIME) immediateTime(data.tiempo,period.code)
                else immediateAreas(data.organizacion,areaId)
            if(data.resumen.total_minutos==0) Text("No existen horas registradas para el periodo y área seleccionados")
            else if(rows.isEmpty()) AggregateRow("Total agregado",bars(data.resumen),data.resumen.total_minutos.coerceAtLeast(1))
            else { val graphMax=rows.maxOf{it.second.total}.coerceAtLeast(1); rows.filter{it.second.total>0}.forEach{AggregateRow(it.first,it.second,graphMax)} }
            Text("Zona horaria: ${data.zona_horaria} · Corte: ${data.fecha_corte}")
        }
        OutlinedButton(onBack,Modifier.fillMaxWidth()){Text("Volver a Informes gerenciales")}
    }
}

@Composable private fun TimeNode(node:ManagementTimeNodeOut,depth:Int,max:Int,selected:String,expanded:List<String>,toggle:(String)->Unit,select:(ManagementTimeNodeOut)->Unit){
    if(node.total_minutos<=0)return; val open=node.codigo in expanded
    Column(Modifier.fillMaxWidth().padding(start=(depth*12).dp,top=3.dp)){
        Row(Modifier.fillMaxWidth().clickable{if(node.hijos.isNotEmpty())toggle(node.codigo)}){Text(if(node.hijos.isEmpty())"• " else if(open)"▼ " else "▶ ");Text(nodeLabel(node),color=if(node.codigo==selected)MaterialTheme.colorScheme.primary else Color.Unspecified)}
        AggregateBar(bars(node),max); TextButton({select(node)}){Text("Seleccionar")}
        if(open)node.hijos.filter{it.total_minutos>0}.forEach{TimeNode(it,depth+1,max,selected,expanded,toggle,select)}
    }
}
@Composable private fun AreaNode(node:ManagementAreaAggregateOut,depth:Int,max:Int,selected:Int?,expanded:List<Int>,toggle:(Int)->Unit,select:(ManagementAreaAggregateOut)->Unit){
    if(node.total_minutos<=0)return; val open=node.id_area in expanded
    Column(Modifier.fillMaxWidth().padding(start=(depth*12).dp,top=3.dp)){
        Row(Modifier.fillMaxWidth().clickable{if(node.hijos.isNotEmpty())toggle(node.id_area)}){Text(if(node.hijos.isEmpty())"• " else if(open)"▼ " else "▶ ");Text("${node.nombre} · ${minutes(node.total_minutos)}",color=if(node.id_area==selected)MaterialTheme.colorScheme.primary else Color.Unspecified)}
        Text(node.ruta,style=MaterialTheme.typography.bodySmall); AggregateBar(bars(node),max); TextButton({select(node)}){Text("Seleccionar")}
        if(open)node.hijos.filter{it.total_minutos>0}.forEach{AreaNode(it,depth+1,max,selected,expanded,toggle,select)}
    }
}
@Composable private fun Indicators(s:ManagementAggregateTotalsOut,generated:String){
    Text("Total: ${minutes(s.total_minutos)}",style=MaterialTheme.typography.titleLarge)
    if(s.ordinarios_minutos>0)Text("Ordinarias: ${minutes(s.ordinarios_minutos)}")
    if(s.extras_minutos>0)Text("Adicionales: ${minutes(s.extras_minutos)}")
    if(s.sabado_minutos>0)Text("Sábado: ${minutes(s.sabado_minutos)}")
    if(s.domingo_minutos+s.festivo_minutos>0)Text("Dominicales/festivas: ${minutes(s.dominicales_festivos_minutos)}")
    if(s.jornadas_incompletas>0)Text("Jornadas incompletas: ${s.jornadas_incompletas}")
    if(s.anomalias>0)Text("Anomalías: ${s.anomalias}")
    Text("Actualizado: $generated",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun ReportLegend(){Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text("Leyenda",style=MaterialTheme.typography.titleSmall)
    Row{Spacer(Modifier.size(12.dp).background(WorkerLightBlue));Text(" Azul claro: jornada inferior")}
    Row{Spacer(Modifier.size(12.dp).background(WorkerDarkBlue));Text(" Azul oscuro: jornada ordinaria completa")}
    Row{Spacer(Modifier.size(12.dp).background(WorkerOvertimeOrange));Text(" Naranja: horas adicionales")}
    Row{Spacer(Modifier.size(12.dp).background(WorkerSundayHolidayRed));Text(" Rojo: domingos y festivos")}
}}
@Composable private fun AggregateRow(label:String,value:BarValues,max:Int){Column(Modifier.fillMaxWidth().padding(vertical=5.dp)){Text("$label · ${minutes(value.total)}");AggregateBar(value,max)}}
@Composable private fun AggregateBar(v:BarValues,max:Int){
    if(v.total<=0)return
    Row(Modifier.fillMaxWidth().height(12.dp)){
        if(v.light>0)Spacer(Modifier.weight(managementBarFraction(v.light,max)).fillMaxHeight().background(WorkerLightBlue))
        if(v.dark>0)Spacer(Modifier.weight(managementBarFraction(v.dark,max)).fillMaxHeight().background(WorkerDarkBlue))
        if(v.extra>0)Spacer(Modifier.weight(managementBarFraction(v.extra,max)).fillMaxHeight().background(WorkerOvertimeOrange))
        if(v.special>0)Spacer(Modifier.weight(managementBarFraction(v.special,max)).fillMaxHeight().background(WorkerSundayHolidayRed))
        val rest=(1f-managementBarFraction(v.total,max)).coerceAtLeast(0f)
        if(rest>0f)Spacer(Modifier.weight(rest).fillMaxHeight())
    }
}

private fun bars(v:ManagementAggregateTotalsOut)=BarValues(v.total_minutos,v.azul_claro_minutos,v.azul_oscuro_minutos,v.extras_minutos,v.dominicales_festivos_minutos)
private fun bars(v:ManagementTimeNodeOut)=BarValues(v.total_minutos,v.azul_claro_minutos,v.azul_oscuro_minutos,v.extras_minutos,v.dominicales_festivos_minutos)
private fun bars(v:ManagementAreaAggregateOut)=BarValues(v.total_minutos,v.azul_claro_minutos,v.azul_oscuro_minutos,v.extras_minutos,v.dominicales_festivos_minutos)
private fun maxVisible(r:ManagementAggregateReportOut)=maxOf(1,r.tiempo.maxOfOrNull{it.total_minutos}?:0,r.organizacion.maxOfOrNull{it.total_minutos}?:0)
private fun nodeLabel(n:ManagementTimeNodeOut)="${if(n.tipo=="MES") YearMonth.parse(n.codigo).month.getDisplayName(TextStyle.FULL,Locale("es")) else n.nombre} · ${minutes(n.total_minutos)}"
private fun periodChoice(n:ManagementTimeNodeOut):PeriodChoice=when(n.tipo){"ANIO"->{val y=n.codigo.toInt();PeriodChoice(n.tipo,n.codigo,n.codigo,LocalDate.of(y,1,1),LocalDate.of(y,12,31))};"MES"->{val m=YearMonth.parse(n.codigo);PeriodChoice(n.tipo,n.codigo,"${m.year} > ${m.month.getDisplayName(TextStyle.FULL,Locale("es"))}",m.atDay(1),m.atEndOfMonth())};else->{val d=LocalDate.parse(n.codigo);PeriodChoice(n.tipo,n.codigo,n.codigo,d,d)}}
private fun immediateTime(roots:List<ManagementTimeNodeOut>,code:String):List<Pair<String,BarValues>>{fun find(nodes:List<ManagementTimeNodeOut>):ManagementTimeNodeOut?{for(n in nodes){if(n.codigo==code)return n;find(n.hijos)?.let{return it}};return null};val n=find(roots)?:return emptyList();return (if(n.hijos.isEmpty())listOf(n)else n.hijos).filter{it.total_minutos>0}.map{nodeLabel(it) to bars(it)}}
private fun immediateAreas(roots:List<ManagementAreaAggregateOut>,id:Int?):List<Pair<String,BarValues>>{val nodes=if(id==null)roots else{fun find(items:List<ManagementAreaAggregateOut>):ManagementAreaAggregateOut?{for(n in items){if(n.id_area==id)return n;find(n.hijos)?.let{return it}};return null};find(roots)?.hijos.orEmpty()};return nodes.filter{it.total_minutos>0}.map{it.nombre to bars(it)}}
private fun <T> toggle(values:List<T>,value:T)=if(value in values)values-value else values+value
internal fun managementBarFraction(minutes:Int,maximum:Int)=
    if(minutes<=0||maximum<=0)0f else minutes.toFloat()/maximum.toFloat()
private fun minutes(value:Int)="${value/60} h ${value%60} min"
