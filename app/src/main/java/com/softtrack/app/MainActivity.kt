package com.softtrack.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Habit(val id: Long = 0, val title: String, val category: String, val kind: String = "good", val schedule: String = "daily", val note: String = "")

// Database schema and backup format intentionally remain compatible with version 1.
class HabitDb(context: Context) : SQLiteOpenHelper(context, "softtrack.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE habits(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,kind TEXT NOT NULL,schedule TEXT NOT NULL,note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,habit_id INTEGER NOT NULL,day TEXT NOT NULL,status INTEGER NOT NULL,UNIQUE(habit_id,day))")
        val samples = listOf(
            Habit(title="Умыться утром",category="Уход за собой"), Habit(title="Умыться вечером",category="Уход за собой"),
            Habit(title="Увлажняющий крем",category="Уход за собой"), Habit(title="SPF при выходе на улицу",category="Уход за собой"),
            Habit(title="Почистить зубы утром",category="Зубы и улыбка"), Habit(title="Почистить зубы вечером",category="Зубы и улыбка"),
            Habit(title="Использовать зубную нить",category="Зубы и улыбка"), Habit(title="Спать 8–10 часов",category="Здоровье и сон"),
            Habit(title="Погулять на свежем воздухе",category="Здоровье и сон"), Habit(title="Пить воду в течение дня",category="Здоровье и сон"),
            Habit(title="Лёгкая разминка",category="Спорт и осанка"), Habit(title="Занятие спортом",category="Спорт и осанка",schedule="weekly"),
            Habit(title="Перерывы при долгом сидении",category="Спорт и осанка"), Habit(title="Следить за чистотой волос",category="Волосы и гигиена"),
            Habit(title="Поддерживать ногти в порядке",category="Волосы и гигиена"), Habit(title="Подготовить одежду",category="Стиль и внешний вид")
        )
        samples.forEach { h -> db.insert("habits", null, ContentValues().apply { put("title",h.title);put("category",h.category);put("kind",h.kind);put("schedule",h.schedule);put("note",h.note) }) }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun allHabits(): List<Habit> = buildList {
        readableDatabase.rawQuery("SELECT * FROM habits ORDER BY category,id",null).use { c -> while(c.moveToNext()) add(Habit(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))) }
    }
    fun addHabit(h: Habit) { writableDatabase.insert("habits",null,ContentValues().apply { put("title",h.title);put("category",h.category);put("kind",h.kind);put("schedule",h.schedule);put("note",h.note) }) }
    fun deleteHabit(id: Long) { writableDatabase.delete("entries","habit_id=?",arrayOf(id.toString()));writableDatabase.delete("habits","id=?",arrayOf(id.toString())) }
    fun status(id: Long, day: String = LocalDate.now().toString()): Int? = readableDatabase.rawQuery("SELECT status FROM entries WHERE habit_id=? AND day=?",arrayOf(id.toString(),day)).use { if(it.moveToFirst()) it.getInt(0) else null }
    fun setStatus(id: Long,status: Int,day: String = LocalDate.now().toString()) { writableDatabase.insertWithOnConflict("entries",null,ContentValues().apply { put("habit_id",id);put("day",day);put("status",status) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun countDone(day: String = LocalDate.now().toString()): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM entries e JOIN habits h ON h.id=e.habit_id WHERE e.day=? AND e.status=1 AND h.kind='good'",arrayOf(day)).use { it.moveToFirst();it.getInt(0) }
    fun exportJson(): String {
        val root=JSONObject();val hs=JSONArray();val es=JSONArray()
        allHabits().forEach { h -> hs.put(JSONObject().put("id",h.id).put("title",h.title).put("category",h.category).put("kind",h.kind).put("schedule",h.schedule).put("note",h.note)) }
        readableDatabase.rawQuery("SELECT habit_id,day,status FROM entries ORDER BY day",null).use { c -> while(c.moveToNext()) es.put(JSONObject().put("habitId",c.getLong(0)).put("day",c.getString(1)).put("status",c.getInt(2))) }
        root.put("format","softtrack-backup").put("version",1).put("exportedAt",System.currentTimeMillis()).put("habits",hs).put("entries",es);return root.toString(2)
    }
    fun importJson(json: String) {
        val root=JSONObject(json);require(root.optString("format")=="softtrack-backup") { "Это не файл резервной копии SoftTrack." };val hs=root.getJSONArray("habits");val es=root.getJSONArray("entries");val db=writableDatabase
        db.beginTransaction();try { db.delete("entries",null,null);db.delete("habits",null,null);val ids=mutableMapOf<Long,Long>()
            for(i in 0 until hs.length()){val x=hs.getJSONObject(i);val v=ContentValues().apply { put("title",x.getString("title"));put("category",x.optString("category","Мои привычки"));put("kind",x.optString("kind","good"));put("schedule",x.optString("schedule","daily"));put("note",x.optString("note","")) };ids[x.optLong("id")]=db.insert("habits",null,v)}
            for(i in 0 until es.length()){val x=es.getJSONObject(i);val newId=ids[x.optLong("habitId")]?:continue;db.insertWithOnConflict("entries",null,ContentValues().apply { put("habit_id",newId);put("day",x.getString("day"));put("status",x.getInt("status")) },SQLiteDatabase.CONFLICT_REPLACE)};db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);setContent { SoftTrackApp(HabitDb(this),getSharedPreferences("settings",MODE_PRIVATE)) } }
}

private val categories=listOf("Уход за собой","Здоровье и сон","Спорт и осанка","Зубы и улыбка","Волосы и гигиена","Стиль и внешний вид","Саморазвитие","Мои привычки")
private enum class Look { NOCTURNE, FIELD }
private data class Palette(val bg:Color,val panel:Color,val panel2:Color,val ink:Color,val muted:Color,val line:Color,val accent:Color,val accentInk:Color,val danger:Color,val editorial:Boolean)

@Composable fun SoftTrackApp(db: HabitDb,prefs: android.content.SharedPreferences) {
    var refresh by remember { mutableIntStateOf(0) };var tab by remember { mutableStateOf(0) }
    var look by remember { mutableStateOf(if(prefs.getString("look","NOCTURNE")=="FIELD") Look.FIELD else Look.NOCTURNE) }
    var accentName by remember { mutableStateOf(prefs.getString("accent","Сланец")?:"Сланец") };var add by remember { mutableStateOf(false) };var notice by remember { mutableStateOf<String?>(null) }
    val context= androidx.compose.ui.platform.LocalContext.current;val habits=remember(refresh){db.allHabits()}
    val accent=when(accentName){"Шалфей"->Color(0xFF9DB59A);"Терракота"->Color(0xFFC77F68);"Охра"->Color(0xFFD1B96F);else->Color(0xFF91AFC2)}
    val p=if(look==Look.NOCTURNE) Palette(Color(0xFF101722),Color(0xFF192331),Color(0xFF202C3B),Color(0xFFF1F3F5),Color(0xFF98A6B8),Color(0xFF344253),accent,Color(0xFF101722),Color(0xFFD98C82),false)
        else Palette(Color(0xFFF0EEE7),Color(0xFFE7E4DA),Color(0xFFE0DDD2),Color(0xFF171714),Color(0xFF77756C),Color(0xFFC9C5B9),when(accentName){"Шалфей"->Color(0xFF8B9F82);"Терракота"->Color(0xFFB96D58);"Сланец"->Color(0xFF9B8E70);else->Color(0xFFB79E54)},Color(0xFF171714),Color(0xFF9A594B),true)
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri:Uri?->if(uri!=null)runCatching{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(db.exportJson())};notice="Резервная копия сохранена"}.onFailure{notice="Не удалось сохранить: ${it.message}"}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->if(uri!=null)runCatching{val s=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Не удалось прочитать файл");db.importJson(s);refresh++;notice="Данные восстановлены"}.onFailure{notice="Ошибка импорта: ${it.message}"}}
    MaterialTheme(colorScheme=if(look==Look.NOCTURNE) darkColorScheme(primary=p.accent,background=p.bg,surface=p.panel,onSurface=p.ink,onBackground=p.ink) else lightColorScheme(primary=p.accent,background=p.bg,surface=p.panel,onSurface=p.ink,onBackground=p.ink)) {
        Scaffold(containerColor=p.bg, bottomBar={ BottomNav(tab,{tab=it},p) }, floatingActionButton={ if(tab<2) FloatingActionButton(onClick={add=true},containerColor=p.accent,contentColor=p.accentInk,shape=if(p.editorial) RoundedCornerShape(5.dp) else CircleShape){Icon(Icons.Default.Add,"Добавить") } }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                Header(tab,p)
                when(tab){0->TodayScreen(habits,db,{refresh++},p);1->HabitsScreen(habits,db,{refresh++},p);2->StatsScreen(habits,db,refresh,p);else->SettingsScreen(look,{look=it;prefs.edit().putString("look",it.name).apply()},accentName,{accentName=it;prefs.edit().putString("accent",it).apply()}, {export.launch("SoftTrack-backup-${LocalDate.now()}.json")},{import.launch(arrayOf("application/json","text/*"))},p)}
            }
        }
        if(add) HabitDialog({add=false},{title,cat,kind,schedule,note->db.addHabit(Habit(title=title,category=cat,kind=kind,schedule=schedule,note=note));refresh++;add=false},p)
        notice?.let { AlertDialog(onDismissRequest={notice=null},confirmButton={TextButton(onClick={notice=null}){Text("Понятно")}},title={Text("SoftTrack")},text={Text(it)},containerColor=p.panel) }
    }
}

@Composable private fun Header(tab:Int,p:Palette){
    val names=listOf("Сегодня","Привычки","Статистика","Настройки");val subtitles=listOf("МАЛЕНЬКИЕ ШАГИ · БОЛЬШИЕ ПЕРЕМЕНЫ","ТВОЯ СИСТЕМА","ПОСЛЕДОВАТЕЛЬНОСТЬ ВАЖНЕЕ ИДЕАЛА","ПАРАМЕТРЫ ПРИЛОЖЕНИЯ")
    Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).padding(top=18.dp,bottom=18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("S / T",color=p.accent,fontWeight=FontWeight.Black,letterSpacing=2.sp);Spacer(Modifier.width(10.dp));Text("SOFTTRACK",color=p.muted,fontSize=11.sp,letterSpacing=2.sp);Spacer(Modifier.weight(1f));Text(if(p.editorial)"FIELD JOURNAL" else "PERSONAL LOG",color=p.muted,fontSize=9.sp,letterSpacing=1.3.sp)}
        Spacer(Modifier.height(17.dp));Text(subtitles[tab],color=p.muted,fontSize=9.sp,letterSpacing=1.4.sp);Text(names[tab],color=p.ink,fontSize=if(p.editorial)34.sp else 30.sp,fontWeight=if(p.editorial)FontWeight.Normal else FontWeight.Bold,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default)
        if(p.editorial) Box(Modifier.padding(top=12.dp).fillMaxWidth().height(1.dp).background(p.line))
    }
}

@Composable private fun BottomNav(selected:Int,onSelect:(Int)->Unit,p:Palette){
    val labels=listOf("День","Список","Итоги","Параметры");val icons=listOf(Icons.Default.WbSunny,Icons.Default.FormatListBulleted,Icons.Default.Timeline,Icons.Default.Tune)
    Row(Modifier.fillMaxWidth().background(p.panel).then(if(p.editorial)Modifier.border(BorderStroke(1.dp,p.line)) else Modifier).navigationBarsPadding().padding(horizontal=7.dp, vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){labels.forEachIndexed{i,label->Column(Modifier.weight(1f).clip(if(p.editorial)RoundedCornerShape(3.dp) else RoundedCornerShape(14.dp)).clickable{onSelect(i)}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icons[i],null,tint=if(selected==i)p.accent else p.muted,modifier=Modifier.size(19.dp));Text(label,color=if(selected==i)p.ink else p.muted,fontSize=9.sp,fontWeight=if(selected==i)FontWeight.Bold else FontWeight.Normal)}}}
}

@Composable private fun SectionLabel(label:String,p:Palette,modifier:Modifier=Modifier){Text(label.uppercase(),modifier.padding(bottom=9.dp),color=p.muted,fontSize=10.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.SemiBold)}
@Composable private fun SurfaceBlock(p:Palette,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().clip(if(p.editorial)RoundedCornerShape(3.dp) else RoundedCornerShape(17.dp)).background(p.panel).then(if(p.editorial)Modifier.border(1.dp,p.line,RoundedCornerShape(3.dp)) else Modifier).padding(16.dp),content=content)}

@Composable private fun TodayScreen(habits:List<Habit>,db:HabitDb,refresh:()->Unit,p:Palette){
    val good=habits.filter{it.kind=="good"};val done=good.count{db.status(it.id)==1};val pct=if(good.isEmpty())0 else done*100/good.size;val progress by animateFloatAsState(if(good.isEmpty())0f else done.toFloat()/good.size,label="daily progress")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(19.dp)){
        SurfaceBlock(p,Modifier){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale("ru"))).replaceFirstChar{it.uppercase()},color=p.muted,fontSize=12.sp);Text("$done",color=p.ink,fontSize=54.sp,fontWeight=FontWeight.Light,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default);Text("ИЗ ${good.size} ПРИВЫЧЕК",color=p.muted,fontSize=9.sp,letterSpacing=1.3.sp)};Text("$pct%",color=p.accent,fontSize=if(p.editorial)40.sp else 32.sp,fontWeight=FontWeight.Light,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default)};Spacer(Modifier.height(15.dp));LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(if(p.editorial)4.dp else 6.dp).clip(CircleShape),color=p.accent,trackColor=p.line)}
        Row(verticalAlignment=Alignment.CenterVertically){SectionLabel("ПЛАН НА СЕГОДНЯ",p,Modifier.weight(1f));Text("${habits.size} ЗАПИСЕЙ",color=p.muted,fontSize=9.sp,letterSpacing=1.sp)}
        if(habits.isEmpty()) EmptyState("Пока здесь пусто","Добавь первую привычку, чтобы начать.",p)
        habits.forEachIndexed{index,h->HabitLine(h,db.status(h.id),{db.setStatus(h.id,it);refresh()},p,index)}
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun HabitLine(h:Habit,status:Int?,onStatus:(Int)->Unit,p:Palette,index:Int=0){
    Row(Modifier.fillMaxWidth().clip(if(p.editorial)RoundedCornerShape(2.dp) else RoundedCornerShape(13.dp)).background(p.panel).then(if(p.editorial)Modifier.border(1.dp,p.line) else Modifier).then(if(h.kind=="good") Modifier.clickable{onStatus(if(status==1)0 else 1)} else Modifier).padding(horizontal=14.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
        if(p.editorial){Text("${(index+1).toString().padStart(2,'0')}",color=p.muted,fontSize=10.sp,modifier=Modifier.width(30.dp))}
        else Box(Modifier.size(7.dp).background(if(status==1)p.accent else p.line,CircleShape))
        Column(Modifier.weight(1f).padding(start=12.dp)){Text(h.title,color=if(status==1)p.muted else p.ink,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(h.category,color=p.muted,fontSize=10.sp)}
        if(h.kind=="good") Box(Modifier.size(25.dp).clip(if(p.editorial)RoundedCornerShape(2.dp) else CircleShape).background(if(status==1)p.accent else Color.Transparent).border(1.dp,if(status==1)p.accent else p.line,if(p.editorial)RoundedCornerShape(2.dp) else CircleShape),contentAlignment=Alignment.Center){if(status==1)Icon(Icons.Default.Check,null,tint=p.accentInk,modifier=Modifier.size(16.dp))}
        else Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={onStatus(1)},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text("Без события",fontSize=9.sp,color=if(status==1)p.accent else p.muted)};TextButton(onClick={onStatus(-1)},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Text("Событие",fontSize=9.sp,color=if(status==-1)p.danger else p.muted)}}
    }
}

@Composable private fun HabitsScreen(habits:List<Habit>,db:HabitDb,refresh:()->Unit,p:Palette){
    var filter by remember{mutableStateOf("Все")};val shown=habits.filter{filter=="Все"||(filter=="Полезные"&&it.kind=="good")||(filter=="Вредные"&&it.kind=="bad")}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.padding(bottom=14.dp)){listOf("Все","Полезные","Вредные").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f,fontSize=11.sp)},shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape,colors=FilterChipDefaults.filterChipColors(selectedContainerColor=p.accent,selectedLabelColor=p.accentInk,containerColor=p.panel,labelColor=p.muted))}}
        if(shown.isEmpty()) EmptyState("Ничего не найдено","Попробуй другой фильтр или добавь привычку.",p) else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=24.dp)){items(shown, key = { it.id }){h->SurfaceBlock(p){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(h.title,color=p.ink,fontWeight=FontWeight.SemiBold);Text("${h.category}  /  ${if(h.kind=="bad")"Наблюдение" else if(h.schedule=="weekly")"Несколько раз в неделю" else "Ежедневно"}",color=p.muted,fontSize=10.sp);if(h.note.isNotBlank())Text(h.note,color=p.muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))};IconButton(onClick={db.deleteHabit(h.id);refresh()}){Icon(Icons.Default.DeleteOutline,null,tint=p.muted)}}
            if(h.kind=="bad"){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={db.setStatus(h.id,1);refresh()},shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape){Text("День без события",fontSize=10.sp)};Button(onClick={db.setStatus(h.id,-1);refresh()},shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape,colors=ButtonDefaults.buttonColors(containerColor=p.accent,contentColor=p.accentInk)){Text("Было событие",fontSize=10.sp)}}}
        }}}}
    }

@Composable private fun StatsScreen(habits:List<Habit>,db:HabitDb,refresh:Int,p:Palette){
    val good=habits.count{it.kind=="good"};val bad=habits.count{it.kind=="bad"};val done=db.countDone();val pct=if(good==0)0 else done*100/good
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("$done","СЕГОДНЯ",p,Modifier.weight(1f));Metric("$good","ПОЛЕЗНЫХ",p,Modifier.weight(1f));Metric("$bad","НАБЛЮДЕНИЙ",p,Modifier.weight(1f))}
        SurfaceBlock(p){SectionLabel("РИТМ ДНЯ",p);Row(verticalAlignment=Alignment.Bottom){Text("$pct",color=p.ink,fontSize=48.sp,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default);Text("% выполнено",color=p.muted,fontSize=12.sp,modifier=Modifier.padding(start=7.dp,bottom=9.dp))};LinearProgressIndicator(progress={if(good==0)0f else done.toFloat()/good},modifier=Modifier.fillMaxWidth().height(5.dp),color=p.accent,trackColor=p.line)}
        SurfaceBlock(p){SectionLabel("ПОСЛЕДНИЕ 7 ДНЕЙ",p);(6 downTo 0).forEach{offset->val date=LocalDate.now().minusDays(offset.toLong()).toString();val count=habits.count{it.kind=="good"&&db.status(it.id,date)==1};val value=if(good==0)0f else count.toFloat()/good;Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EE",Locale("ru"))).uppercase(),color=p.muted,fontSize=9.sp,modifier=Modifier.width(37.dp));Box(Modifier.weight(1f).height(if(p.editorial)5.dp else 7.dp).background(p.line,if(p.editorial)RoundedCornerShape(0.dp) else CircleShape)){Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(p.accent,if(p.editorial)RoundedCornerShape(0.dp) else CircleShape))};Text("$count/$good",color=p.ink,fontSize=10.sp,modifier=Modifier.width(45.dp).wrapContentWidth(Alignment.End))}}}
        Text("Прогресс — это наблюдение за привычками, а не оценка тебя.",color=p.muted,fontSize=11.sp)
    }
}
@Composable private fun Metric(value:String,label:String,p:Palette,modifier:Modifier){Column(modifier.clip(if(p.editorial)RoundedCornerShape(2.dp) else RoundedCornerShape(14.dp)).background(p.panel).then(if(p.editorial)Modifier.border(1.dp,p.line) else Modifier).padding(12.dp)){Text(value,color=p.ink,fontSize=29.sp,fontWeight=FontWeight.Light,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default);Text(label,color=p.muted,fontSize=8.sp,letterSpacing=.8.sp)} }

@Composable private fun SettingsScreen(look:Look,onLook:(Look)->Unit,accentName:String,onAccent:(String)->Unit,export:()->Unit,import:()->Unit,p:Palette){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        SurfaceBlock(p){SectionLabel("ВИЗУАЛЬНАЯ СИСТЕМА",p);LookPreview("NOCTURNE","Графит · холодный воздух · спокойный контраст",look==Look.NOCTURNE,Color(0xFF101722),Color(0xFF91AFC2),p){onLook(Look.NOCTURNE)};Spacer(Modifier.height(8.dp));LookPreview("FIELD NOTES","Бумага · редакционная сетка · тёплый тон",look==Look.FIELD,Color(0xFFF0EEE7),Color(0xFFB79E54),p){onLook(Look.FIELD)}}
        SurfaceBlock(p){SectionLabel("АКЦЕНТ",p);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("Сланец" to Color(0xFF91AFC2),"Шалфей" to Color(0xFF9DB59A),"Терракота" to Color(0xFFC77F68),"Охра" to Color(0xFFD1B96F)).forEach{(name,color)->Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).clickable{onAccent(name)}.padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(25.dp).background(color,CircleShape).then(if(accentName==name)Modifier.border(2.dp,p.ink,CircleShape) else Modifier));Text(name,color=p.muted,fontSize=8.sp)}}}}
        SurfaceBlock(p){SectionLabel("ДАННЫЕ И РЕЗЕРВНЫЕ КОПИИ",p);Text("Экспорт сохраняет привычки и историю в JSON. Импорт заменяет текущие данные содержимым выбранной копии.",color=p.muted,fontSize=11.sp,lineHeight=17.sp);Spacer(Modifier.height(12.dp));Button(onClick=export,modifier=Modifier.fillMaxWidth(),shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape,colors=ButtonDefaults.buttonColors(containerColor=p.accent,contentColor=p.accentInk)){Icon(Icons.Default.FileDownload,null);Spacer(Modifier.width(8.dp));Text("Экспортировать JSON")};OutlinedButton(onClick=import,modifier=Modifier.fillMaxWidth(),shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape){Icon(Icons.Default.FileUpload,null);Spacer(Modifier.width(8.dp));Text("Импортировать JSON")}}
        Text("SOFTTRACK  ·  VERSION 1.1.0",color=p.muted,fontSize=9.sp,letterSpacing=1.2.sp,modifier=Modifier.padding(bottom=20.dp))
    }
}
@Composable private fun LookPreview(title:String,desc:String,selected:Boolean,bg:Color,accent:Color,p:Palette,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if(selected)p.panel2 else Color.Transparent).clickable{onClick()}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=p.ink,fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.sp);Text(desc,color=p.muted,fontSize=9.sp)};Row(Modifier.width(62.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(bg).padding(6.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Box(Modifier.fillMaxWidth(.8f).height(4.dp).background(accent));Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha=.3f)))};if(selected)Icon(Icons.Default.CheckCircle,null,tint=accent,modifier=Modifier.size(14.dp))}}}

@Composable private fun EmptyState(title:String,desc:String,p:Palette){Column(Modifier.fillMaxWidth().padding(vertical=35.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=p.ink,fontSize=18.sp,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default);Text(desc,color=p.muted,fontSize=11.sp)}}

@Composable private fun HabitDialog(onDismiss:()->Unit,onSave:(String,String,String,String,String)->Unit,p:Palette){
    var title by remember{mutableStateOf("")};var category by remember{mutableStateOf(categories.first())};var kind by remember{mutableStateOf("good")};var schedule by remember{mutableStateOf("daily")};var note by remember{mutableStateOf("")};var expanded by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,containerColor=p.panel,shape=if(p.editorial)RoundedCornerShape(4.dp) else RoundedCornerShape(22.dp),title={Text("Новая привычка",color=p.ink,fontFamily=if(p.editorial)FontFamily.Serif else FontFamily.Default)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        OutlinedTextField(value=title,onValueChange={title=it},label={Text("Название")},singleLine=true)
        Text("ТИП",color=p.muted,fontSize=9.sp,letterSpacing=1.sp);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){FilterChip(selected=kind=="good",onClick={kind="good"},label={Text("Полезная")});FilterChip(selected=kind=="bad",onClick={kind="bad"},label={Text("Наблюдение")})}
        Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(category);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){categories.forEach{DropdownMenuItem(text={Text(it)},onClick={category=it;expanded=false})}}}
        if(kind=="good")Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){FilterChip(selected=schedule=="daily",onClick={schedule="daily"},label={Text("Ежедневно")});FilterChip(selected=schedule=="weekly",onClick={schedule="weekly"},label={Text("Несколько раз в неделю")})}
        OutlinedTextField(value=note,onValueChange={note=it},label={Text("Заметка · необязательно")},minLines=1,maxLines=2)
    }},confirmButton={Button(onClick={if(title.isNotBlank())onSave(title.trim(),category,kind,schedule,note.trim())},enabled=title.isNotBlank(),shape=if(p.editorial)RoundedCornerShape(3.dp) else CircleShape){Text("Добавить")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
