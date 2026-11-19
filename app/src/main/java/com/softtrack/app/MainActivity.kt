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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Habit(
    val id: Long = 0,
    val title: String,
    val category: String,
    val kind: String = "good",
    val schedule: String = "daily",
    val note: String = ""
)

class HabitDb(context: Context) : SQLiteOpenHelper(context, "softtrack.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE habits(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL, category TEXT NOT NULL, kind TEXT NOT NULL,
            schedule TEXT NOT NULL, note TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE entries(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            habit_id INTEGER NOT NULL, day TEXT NOT NULL, status INTEGER NOT NULL,
            UNIQUE(habit_id, day))""")
        seed(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val samples = listOf(
            Habit(title="Умыться утром", category="Уход за собой"),
            Habit(title="Умыться вечером", category="Уход за собой"),
            Habit(title="Увлажняющий крем", category="Уход за собой"),
            Habit(title="SPF при выходе на улицу", category="Уход за собой"),
            Habit(title="Почистить зубы утром", category="Зубы и улыбка"),
            Habit(title="Почистить зубы вечером", category="Зубы и улыбка"),
            Habit(title="Использовать зубную нить", category="Зубы и улыбка"),
            Habit(title="Спать 8–10 часов", category="Здоровье и сон"),
            Habit(title="Погулять на свежем воздухе", category="Здоровье и сон"),
            Habit(title="Пить воду в течение дня", category="Здоровье и сон"),
            Habit(title="Лёгкая разминка", category="Спорт и осанка"),
            Habit(title="Занятие спортом", category="Спорт и осанка", schedule="weekly"),
            Habit(title="Перерывы при долгом сидении", category="Спорт и осанка"),
            Habit(title="Следить за чистотой волос", category="Волосы и гигиена"),
            Habit(title="Поддерживать ногти в порядке", category="Волосы и гигиена"),
            Habit(title="Подготовить одежду", category="Стиль и внешний вид")
        )
        samples.forEach { insertHabit(db, it) }
    }

    private fun insertHabit(db: SQLiteDatabase, h: Habit) {
        val v = ContentValues().apply {
            put("title", h.title); put("category", h.category); put("kind", h.kind)
            put("schedule", h.schedule); put("note", h.note)
        }
        db.insert("habits", null, v)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun allHabits(): List<Habit> {
        val result = mutableListOf<Habit>()
        readableDatabase.rawQuery("SELECT * FROM habits ORDER BY category, id", null).use { c ->
            while (c.moveToNext()) result += Habit(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)
            )
        }
        return result
    }

    fun addHabit(h: Habit) {
        val v = ContentValues().apply {
            put("title", h.title); put("category", h.category); put("kind", h.kind)
            put("schedule", h.schedule); put("note", h.note)
        }
        writableDatabase.insert("habits", null, v)
    }

    fun deleteHabit(id: Long) {
        writableDatabase.delete("entries", "habit_id=?", arrayOf(id.toString()))
        writableDatabase.delete("habits", "id=?", arrayOf(id.toString()))
    }

    fun status(id: Long, day: String = LocalDate.now().toString()): Int? {
        readableDatabase.rawQuery("SELECT status FROM entries WHERE habit_id=? AND day=?", arrayOf(id.toString(), day)).use {
            return if (it.moveToFirst()) it.getInt(0) else null
        }
    }

    fun setStatus(id: Long, status: Int, day: String = LocalDate.now().toString()) {
        val v = ContentValues().apply { put("habit_id", id); put("day", day); put("status", status) }
        writableDatabase.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun countDone(day: String = LocalDate.now().toString()): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM entries WHERE day=? AND status=1", arrayOf(day)).use {
            it.moveToFirst(); return it.getInt(0)
        }
    }

    fun totalGood(): Int = allHabits().count { it.kind == "good" }

    fun exportJson(): String {
        val root = JSONObject()
        val hs = JSONArray()
        allHabits().forEach { h ->
            hs.put(JSONObject().put("id", h.id).put("title", h.title).put("category", h.category)
                .put("kind", h.kind).put("schedule", h.schedule).put("note", h.note))
        }
        val es = JSONArray()
        readableDatabase.rawQuery("SELECT habit_id,day,status FROM entries ORDER BY day", null).use { c ->
            while (c.moveToNext()) es.put(JSONObject().put("habitId", c.getLong(0))
                .put("day", c.getString(1)).put("status", c.getInt(2)))
        }
        root.put("format", "softtrack-backup")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("habits", hs)
        root.put("entries", es)
        return root.toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "softtrack-backup") { "Это не файл резервной копии SoftTrack." }
        val hs = root.getJSONArray("habits")
        val es = root.getJSONArray("entries")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", null, null)
            db.delete("habits", null, null)
            val idMap = mutableMapOf<Long, Long>()
            for (i in 0 until hs.length()) {
                val x = hs.getJSONObject(i)
                val values = ContentValues().apply {
                    put("title", x.getString("title")); put("category", x.optString("category", "Мои привычки"))
                    put("kind", x.optString("kind", "good")); put("schedule", x.optString("schedule", "daily"))
                    put("note", x.optString("note", ""))
                }
                val newId = db.insert("habits", null, values)
                idMap[x.optLong("id")] = newId
            }
            for (i in 0 until es.length()) {
                val x = es.getJSONObject(i)
                val newId = idMap[x.optLong("habitId")] ?: continue
                val values = ContentValues().apply {
                    put("habit_id", newId); put("day", x.getString("day")); put("status", x.getInt("status"))
                }
                db.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = HabitDb(this)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent { SoftTrackApp(db, prefs) }
    }
}

private val categories = listOf(
    "Уход за собой", "Здоровье и сон", "Спорт и осанка",
    "Зубы и улыбка", "Волосы и гигиена", "Стиль и внешний вид", "Саморазвитие", "Мои привычки"
)

@Composable
fun SoftTrackApp(db: HabitDb, prefs: android.content.SharedPreferences) {
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf("Сегодня") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var accentName by remember { mutableStateOf(prefs.getString("accent", "Голубой") ?: "Голубой") }
    var showAdd by remember { mutableStateOf(false) }
    var showBadAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val habits = remember(refresh) { db.allHabits() }
    val accent = when (accentName) {
        "Фиолетовый" -> Color(0xFFB69CFF)
        "Изумрудный" -> Color(0xFF55D6A0)
        "Красный" -> Color(0xFFFF7777)
        "Оранжевый" -> Color(0xFFFFB36B)
        else -> Color(0xFF62C7F5)
    }
    val bg = if (dark) Color(0xFF0C111B) else Color(0xFFF4F7FB)
    val surface = if (dark) Color(0xFF171F2D) else Color.White
    val text = if (dark) Color(0xFFF2F6FC) else Color(0xFF172033)
    val sub = if (dark) Color(0xFF9BA9BC) else Color(0xFF65748B)
    val launcherExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportJson()) }
            message = "Резервная копия сохранена"
        }.onFailure { message = "Не удалось сохранить: ${it.message}" }
    }
    val launcherImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val data = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Не удалось прочитать файл")
            db.importJson(data); refresh++; message = "Данные восстановлены"
        }.onFailure { message = "Ошибка импорта: ${it.message}" }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = accent, background = bg, surface = surface)
        else lightColorScheme(primary = accent, background = bg, surface = surface)) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                NavigationBar(containerColor = surface) {
                    listOf("Сегодня", "Привычки", "Статистика", "Настройки").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(when(item) {
                                    "Сегодня" -> Icons.Default.Today
                                    "Привычки" -> Icons.Default.CheckCircle
                                    "Статистика" -> Icons.Default.Insights
                                    else -> Icons.Default.Settings
                                }, contentDescription = item)
                            },
                            label = { Text(item) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == "Привычки" || tab == "Сегодня") {
                    FloatingActionButton(onClick = { showAdd = true }, containerColor = accent, contentColor = Color(0xFF07121B)) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить привычку")
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(18.dp))
                Text("SOFTTRACK", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(when(tab) {
                    "Сегодня" -> "Твой день"
                    "Привычки" -> "Мои привычки"
                    "Статистика" -> "Твой прогресс"
                    else -> "Настройки"
                }, color = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    "Сегодня" -> TodayScreen(habits, db, refresh, { refresh++ }, surface, text, sub, accent, { showAdd = true })
                    "Привычки" -> HabitsScreen(habits, db, refresh, { refresh++ }, surface, text, sub, accent, { showAdd = true })
                    "Статистика" -> StatsScreen(habits, db, refresh, surface, text, sub, accent)
                    else -> SettingsScreen(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                        accentName, { accentName = it; prefs.edit().putString("accent", it).apply() },
                        { launcherExport.launch("SoftTrack-backup-${LocalDate.now()}.json") },
                        { launcherImport.launch(arrayOf("application/json", "text/*")) }, surface, text, sub, accent)
                }
            }
        }
        if (showAdd) HabitDialog(
            onDismiss = { showAdd = false },
            onSave = { title, category, kind, schedule, note ->
                db.addHabit(Habit(title = title, category = category, kind = kind, schedule = schedule, note = note))
                refresh++; showAdd = false
            },
            surface = surface, text = text, accent = accent
        )
        message?.let { msg ->
            AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ОК") } },
                title = { Text("SoftTrack") }, text = { Text(msg) }, containerColor = surface)
        }
    }
}

@Composable
fun TodayScreen(habits: List<Habit>, db: HabitDb, refresh: Int, onRefresh: () -> Unit,
                surface: Color, text: Color, sub: Color, accent: Color, add: () -> Unit) {
    val regular = habits.filter { it.kind == "good" }
    val done = regular.count { db.status(it.id) == 1 }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM")), color = sub)
                        Text("$done из ${regular.size}", color = text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("привычек выполнено", color = sub)
                    }
                    Box(Modifier.size(74.dp).background(accent.copy(alpha=.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (regular.isEmpty()) "0%" else "${done * 100 / regular.size}%", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (regular.isEmpty()) 0f else done.toFloat()/regular.size }, modifier = Modifier.fillMaxWidth().height(7.dp), color = accent, trackColor = accent.copy(alpha=.15f))
            }
        }
        Text("На сегодня", color = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(habits, key = { it.id }) { h ->
                HabitRow(h, db.status(h.id), { status -> db.setStatus(h.id, status); onRefresh() }, surface, text, sub, accent)
            }
        }
    }
}

@Composable
fun HabitsScreen(habits: List<Habit>, db: HabitDb, refresh: Int, onRefresh: () -> Unit,
                 surface: Color, text: Color, sub: Color, accent: Color, add: () -> Unit) {
    var filter by remember { mutableStateOf("Все") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "Полезные", "Вредные").forEach {
                FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            val shown = habits.filter { filter == "Все" || (filter == "Полезные" && it.kind == "good") || (filter == "Вредные" && it.kind == "bad") }
            items(shown, key = { it.id }) { h ->
                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.title, color = text, fontWeight = FontWeight.SemiBold)
                                Text("${h.category} • ${if(h.kind=="bad") "Вредная привычка" else "Полезная привычка"}", color = sub, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { db.deleteHabit(h.id); onRefresh() }) { Icon(Icons.Default.DeleteOutline, null, tint = sub) }
                        }
                        if (h.kind == "bad") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { db.setStatus(h.id, 1); onRefresh() }) { Text("День без события") }
                                Button(onClick = { db.setStatus(h.id, -1); onRefresh() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Было событие") }
                            }
                        } else {
                            Text("Расписание: ${if(h.schedule=="weekly") "несколько раз в неделю" else "ежедневно"}", color = sub, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitRow(h: Habit, status: Int?, onStatus: (Int) -> Unit, surface: Color, text: Color, sub: Color, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.title, color = text, fontWeight = FontWeight.Medium)
                Text(h.category, color = sub, style = MaterialTheme.typography.bodySmall)
                if (h.kind == "bad") Text("Отслеживание без осуждения", color = sub, style = MaterialTheme.typography.labelSmall)
            }
            if (h.kind == "good") {
                Checkbox(checked = status == 1, onCheckedChange = { onStatus(if (it) 1 else 0) }, colors = CheckboxDefaults.colors(checkedColor = accent))
            } else {
                IconButton(onClick = { onStatus(if(status == -1) 0 else -1) }) {
                    Icon(if(status == -1) Icons.Default.Flag else Icons.Default.CheckCircleOutline,
                        contentDescription = "Отметить событие", tint = if(status == -1) Color(0xFFFF8A80) else accent)
                }
            }
        }
    }
}

@Composable
fun StatsScreen(habits: List<Habit>, db: HabitDb, refresh: Int, surface: Color, text: Color, sub: Color, accent: Color) {
    val done = db.countDone()
    val good = habits.count { it.kind == "good" }
    val bad = habits.count { it.kind == "bad" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Выполнено сегодня", "$done", surface, text, sub, accent)
        StatCard("Полезных привычек", "$good", surface, text, sub, accent)
        StatCard("Отслеживается вредных привычек", "$bad", surface, text, sub, accent)
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Последние 7 дней", color = text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                (6 downTo 0).forEach { offset ->
                    val date = LocalDate.now().minusDays(offset.toLong()).toString()
                    val count = habits.count { it.kind == "good" && db.status(it.id, date) == 1 }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE")), color = sub, modifier = Modifier.width(48.dp))
                        LinearProgressIndicator(progress = { if(good==0) 0f else count.toFloat()/good }, modifier = Modifier.weight(1f).height(7.dp), color = accent, trackColor = accent.copy(alpha=.13f))
                        Text("$count/$good", color = text, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, surface: Color, text: Color, sub: Color, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = sub); Text(value, color = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            Icon(Icons.Default.AutoGraph, null, tint = accent)
        }
    }
}

@Composable
fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, accentName: String, onAccent: (String) -> Unit,
                   export: () -> Unit, import: () -> Unit, surface: Color, text: Color, sub: Color, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Внешний вид", color = text, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Тёмная тема", color = text, modifier = Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = onDark)
                }
                Text("Акцентный цвет", color = sub)
                listOf("Голубой", "Фиолетовый", "Изумрудный", "Красный", "Оранжевый").forEach { color ->
                    Row(Modifier.fillMaxWidth().clickable { onAccent(color) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).background(when(color) {
                            "Фиолетовый" -> Color(0xFFB69CFF); "Изумрудный" -> Color(0xFF55D6A0)
                            "Красный" -> Color(0xFFFF7777); "Оранжевый" -> Color(0xFFFFB36B)
                            else -> Color(0xFF62C7F5)
                        }, CircleShape))
                        Text(color, color = text, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        if (accentName == color) Icon(Icons.Default.Check, null, tint = accent)
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Резервная копия", color = text, fontWeight = FontWeight.SemiBold)
                Text("Сохраняет привычки и историю в JSON-файл. Храни его в безопасном месте.", color = sub, style = MaterialTheme.typography.bodySmall)
                Button(onClick = export, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Экспортировать данные") }
                OutlinedButton(onClick = import, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Импортировать данные") }
            }
        }
        Text("SoftTrack • версия 1.0.0", color = sub, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun HabitDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit,
                surface: Color, text: Color, accent: Color) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }
    var kind by remember { mutableStateOf("good") }
    var schedule by remember { mutableStateOf("daily") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface,
        title = { Text("Новая привычка", color = text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                Text("Тип", color = text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = kind=="good", onClick = { kind="good" }, label = { Text("Полезная") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = kind=="bad", onClick = { kind="bad" }, label = { Text("Вредная") })
                }
                Text("Категория", color = text)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(category) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category=it; expanded=false }) }
                    }
                }
                if (kind=="good") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = schedule=="daily", onClick = { schedule="daily" }, label = { Text("Ежедневно") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = schedule=="weekly", onClick = { schedule="weekly" }, label = { Text("По неделям") })
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note=it }, label = { Text("Заметка (необязательно)") }, minLines = 1, maxLines = 2)
            }
        },
        confirmButton = { Button(onClick = { if(title.isNotBlank()) onSave(title.trim(), category, kind, schedule, note.trim()) }, enabled = title.isNotBlank()) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
