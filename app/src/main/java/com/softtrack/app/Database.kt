package com.softtrack.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private const val HCOLS = "id,title,category,kind,note,favorite,archived,weekly_goal,reminders_on,created_day"

fun parseDay(s: String?): LocalDate = try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.now() }

/**
 * softtrack.db. Версия 1 -> 2: новые колонки habits, таблицы habit_rules и day_notes.
 * База не пересоздаётся: старые привычки и история переносятся миграцией.
 */
class HabitDb private constructor(context: Context) : SQLiteOpenHelper(context, "softtrack.db", null, 2) {
    companion object {
        @Volatile private var inst: HabitDb? = null
        fun get(ctx: Context): HabitDb = inst ?: synchronized(this) {
            inst ?: HabitDb(ctx.applicationContext).also { inst = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE habits(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,category TEXT NOT NULL,kind TEXT NOT NULL,schedule TEXT NOT NULL,note TEXT NOT NULL,favorite INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,weekly_goal INTEGER NOT NULL DEFAULT 0,reminders_on INTEGER NOT NULL DEFAULT 1,created_day TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,habit_id INTEGER NOT NULL,day TEXT NOT NULL,status INTEGER NOT NULL,UNIQUE(habit_id,day))")
        createExtras(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE habits ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN weekly_goal INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN reminders_on INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE habits ADD COLUMN created_day TEXT NOT NULL DEFAULT ''")
            createExtras(db)
            val today = LocalDate.now()
            db.execSQL(
                "UPDATE habits SET created_day = COALESCE((SELECT MIN(day) FROM entries WHERE entries.habit_id = habits.id), ?)",
                arrayOf<Any?>(today.toString())
            )
            db.execSQL("UPDATE habits SET weekly_goal = 3 WHERE kind = 'good' AND schedule = 'weekly'")
            val old = ArrayList<Triple<Long, String, String>>()
            db.rawQuery("SELECT id,kind,schedule FROM habits", null).use { c ->
                while (c.moveToNext()) old.add(Triple(c.getLong(0), c.getString(1), c.getString(2)))
            }
            old.forEach { insertLegacyRules(db, it.first, it.second, it.third, today) }
        }
    }

    private fun createExtras(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE habit_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,habit_id INTEGER NOT NULL,dow INTEGER NOT NULL,minutes INTEGER NOT NULL,from_day TEXT NOT NULL,to_day TEXT)")
        db.execSQL("CREATE INDEX idx_rules_habit ON habit_rules(habit_id)")
        db.execSQL("CREATE TABLE day_notes(habit_id INTEGER NOT NULL,day TEXT NOT NULL,text TEXT NOT NULL,PRIMARY KEY(habit_id,day))")
    }

    /**
     * Перенос старого расписания. «Ежедневно» и наблюдения — все 7 дней с самого начала истории.
     * Старое «несколько раз в неделю» не имело дней, поэтому с даты обновления это Пн/Ср/Пт с целью 3 в неделю,
     * а прошлые дни остаются без расписания (ложных пропусков нет).
     */
    private fun insertLegacyRules(db: SQLiteDatabase, id: Long, kind: String, schedule: String, today: LocalDate) {
        if (kind == "good" && schedule == "weekly") {
            listOf(1, 3, 5).forEach { insertRule(db, id, it, -1, today, null) }
        } else {
            (1..7).forEach { insertRule(db, id, it, -1, EPOCH, null) }
        }
    }

    private fun insertRule(db: SQLiteDatabase, habitId: Long, dow: Int, minutes: Int, from: LocalDate, to: LocalDate?) {
        db.insert("habit_rules", null, ContentValues().apply {
            put("habit_id", habitId); put("dow", dow); put("minutes", minutes)
            put("from_day", from.toString())
            if (to == null) putNull("to_day") else put("to_day", to.toString())
        })
    }

    private fun insertHabitRow(db: SQLiteDatabase, h: Habit, legacySchedule: String): Long =
        db.insert("habits", null, ContentValues().apply {
            put("title", h.title); put("category", h.category); put("kind", h.kind)
            put("schedule", legacySchedule); put("note", h.note)
            put("favorite", if (h.favorite) 1 else 0); put("archived", if (h.archived) 1 else 0)
            put("weekly_goal", h.weeklyGoal); put("reminders_on", if (h.remindersOn) 1 else 0)
            put("created_day", h.createdDay.toString())
        })

    private fun readHabit(c: Cursor) = Habit(
        id = c.getLong(0), title = c.getString(1), category = c.getString(2), kind = c.getString(3), note = c.getString(4),
        favorite = c.getInt(5) == 1, archived = c.getInt(6) == 1, weeklyGoal = c.getInt(7),
        remindersOn = c.getInt(8) == 1, createdDay = parseDay(c.getString(9))
    )

    // ---------- чтение ----------

    fun loadSnapshot(): Snapshot {
        val db = readableDatabase
        val habits = ArrayList<Habit>()
        db.rawQuery("SELECT $HCOLS FROM habits ORDER BY category,id", null).use { c -> while (c.moveToNext()) habits.add(readHabit(c)) }
        val rules = HashMap<Long, MutableList<Rule>>()
        db.rawQuery("SELECT habit_id,dow,minutes,from_day,to_day FROM habit_rules", null).use { c ->
            while (c.moveToNext()) {
                val r = Rule(c.getLong(0), c.getInt(1), c.getInt(2), parseDay(c.getString(3)), if (c.isNull(4)) null else parseDay(c.getString(4)))
                rules.getOrPut(r.habitId) { ArrayList() }.add(r)
            }
        }
        val entries = HashMap<Long, MutableMap<LocalDate, Int>>()
        db.rawQuery("SELECT habit_id,day,status FROM entries", null).use { c ->
            while (c.moveToNext()) entries.getOrPut(c.getLong(0)) { HashMap() }[parseDay(c.getString(1))] = c.getInt(2)
        }
        val notes = HashMap<Long, MutableMap<LocalDate, String>>()
        db.rawQuery("SELECT habit_id,day,text FROM day_notes", null).use { c ->
            while (c.moveToNext()) notes.getOrPut(c.getLong(0)) { HashMap() }[parseDay(c.getString(1))] = c.getString(2)
        }
        return Snapshot(habits, rules, entries, notes)
    }

    // ---------- запись ----------

    /** Создаёт или обновляет привычку и расписание одной транзакцией. */
    fun saveHabit(h: Habit, inputs: List<RuleInput>, today: LocalDate): Long {
        val db = writableDatabase
        var id = h.id
        db.beginTransaction()
        try {
            val legacy = if (inputs.map { it.dow }.distinct().size == 7) "daily" else "weekly"
            if (id == 0L) {
                id = insertHabitRow(db, h.copy(createdDay = today), legacy)
            } else {
                db.update("habits", ContentValues().apply {
                    put("title", h.title); put("category", h.category); put("kind", h.kind); put("schedule", legacy)
                    put("note", h.note); put("favorite", if (h.favorite) 1 else 0)
                    put("weekly_goal", h.weeklyGoal); put("reminders_on", if (h.remindersOn) 1 else 0)
                }, "id=?", arrayOf(id.toString()))
            }
            if (!h.archived) applyRules(db, id, inputs, today)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    private fun applyRules(db: SQLiteDatabase, id: Long, inputs: List<RuleInput>, today: LocalDate) {
        val t = today.toString()
        val current = HashSet<Pair<Int, Int>>()
        db.rawQuery("SELECT dow,minutes FROM habit_rules WHERE habit_id=? AND to_day IS NULL", arrayOf(id.toString())).use { c ->
            while (c.moveToNext()) current.add(c.getInt(0) to c.getInt(1))
        }
        val wanted = inputs.map { it.dow to it.minutes }.toSet()
        if (current == wanted) return
        closeOpenRules(db, id, t, deleteSameDay = true)
        inputs.forEach { insertRule(db, id, it.dow, it.minutes, today, null) }
    }

    private fun closeOpenRules(db: SQLiteDatabase, id: Long, t: String, deleteSameDay: Boolean) {
        if (deleteSameDay) db.delete("habit_rules", "habit_id=? AND to_day IS NULL AND from_day>=?", arrayOf(id.toString(), t))
        db.execSQL("UPDATE habit_rules SET to_day=? WHERE habit_id=? AND to_day IS NULL", arrayOf<Any?>(t, id))
    }

    fun setArchived(id: Long, archived: Boolean, today: LocalDate) {
        val db = writableDatabase
        val t = today.toString()
        db.beginTransaction()
        try {
            if (archived) {
                closeOpenRules(db, id, t, deleteSameDay = false)
            } else {
                val restore = ArrayList<Pair<Int, Int>>()
                db.rawQuery(
                    "SELECT dow,minutes FROM habit_rules WHERE habit_id=? AND to_day=(SELECT MAX(to_day) FROM habit_rules WHERE habit_id=? AND to_day IS NOT NULL)",
                    arrayOf(id.toString(), id.toString())
                ).use { c -> while (c.moveToNext()) restore.add(c.getInt(0) to c.getInt(1)) }
                if (restore.isEmpty()) (1..7).forEach { restore.add(it to -1) }
                restore.forEach { insertRule(db, id, it.first, it.second, today, null) }
            }
            db.update("habits", ContentValues().apply { put("archived", if (archived) 1 else 0) }, "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setFavorite(id: Long, fav: Boolean) {
        writableDatabase.update("habits", ContentValues().apply { put("favorite", if (fav) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    fun deleteHabit(id: Long) {
        val db = writableDatabase
        val a = arrayOf(id.toString())
        db.beginTransaction()
        try {
            db.delete("entries", "habit_id=?", a)
            db.delete("habit_rules", "habit_id=?", a)
            db.delete("day_notes", "habit_id=?", a)
            db.delete("habits", "id=?", a)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** status: 1 — выполнено / день без события, -1 — было событие, 0 — снять отметку. */
    fun setStatus(id: Long, day: String, status: Int) {
        val db = writableDatabase
        if (status == 0) {
            db.delete("entries", "habit_id=? AND day=?", arrayOf(id.toString(), day))
        } else {
            db.insertWithOnConflict("entries", null, ContentValues().apply {
                put("habit_id", id); put("day", day); put("status", status)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun setNote(id: Long, day: String, text: String) {
        val db = writableDatabase
        if (text.isBlank()) {
            db.delete("day_notes", "habit_id=? AND day=?", arrayOf(id.toString(), day))
        } else {
            db.insertWithOnConflict("day_notes", null, ContentValues().apply {
                put("habit_id", id); put("day", day); put("text", text.trim())
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ---------- резервные копии ----------

    /** Формат softtrack-backup версии 2. Файлы версии 1 по-прежнему читаются. */
    fun exportJson(): String {
        val s = loadSnapshot()
        val hs = JSONArray()
        val rs = JSONArray()
        val es = JSONArray()
        val ns = JSONArray()
        s.habits.forEach { h ->
            hs.put(JSONObject().put("id", h.id).put("title", h.title).put("category", h.category).put("kind", h.kind)
                .put("schedule", if (s.lastRuleInputs(h).map { it.dow }.distinct().size == 7) "daily" else "weekly")
                .put("note", h.note).put("favorite", h.favorite).put("archived", h.archived)
                .put("weeklyGoal", h.weeklyGoal).put("remindersOn", h.remindersOn).put("createdDay", h.createdDay.toString()))
            s.rulesOf(h.id).forEach { r ->
                rs.put(JSONObject().put("habitId", r.habitId).put("dow", r.dow).put("minutes", r.minutes)
                    .put("from", r.from.toString()).put("to", r.to?.toString() ?: JSONObject.NULL))
            }
            s.notesOf(h).forEach { (d, t) -> ns.put(JSONObject().put("habitId", h.id).put("day", d.toString()).put("text", t)) }
        }
        readableDatabase.rawQuery("SELECT habit_id,day,status FROM entries ORDER BY day", null).use { c ->
            while (c.moveToNext()) es.put(JSONObject().put("habitId", c.getLong(0)).put("day", c.getString(1)).put("status", c.getInt(2)))
        }
        return JSONObject().put("format", "softtrack-backup").put("version", 2).put("exportedAt", System.currentTimeMillis())
            .put("habits", hs).put("rules", rs).put("entries", es).put("notes", ns).toString(2)
    }

    /** Импорт заменяет текущие данные. Принимает версии 1 и 2. */
    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "softtrack-backup") { "Это не файл резервной копии SoftTrack." }
        val version = root.optInt("version", 1)
        require(version <= 2) { "Копия создана более новой версией SoftTrack." }
        val hs = root.getJSONArray("habits")
        val es = root.optJSONArray("entries") ?: JSONArray()
        val rs = root.optJSONArray("rules") ?: JSONArray()
        val ns = root.optJSONArray("notes") ?: JSONArray()
        val today = LocalDate.now()

        val firstEntry = HashMap<Long, String>()
        for (i in 0 until es.length()) {
            val x = es.getJSONObject(i)
            val hid = x.optLong("habitId")
            val day = x.getString("day")
            val cur = firstEntry[hid]
            if (cur == null || day < cur) firstEntry[hid] = day
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", null, null)
            db.delete("habit_rules", null, null)
            db.delete("day_notes", null, null)
            db.delete("habits", null, null)
            val ids = HashMap<Long, Long>()
            val withRules = HashSet<Long>()
            for (i in 0 until rs.length()) withRules.add(rs.getJSONObject(i).optLong("habitId"))
            for (i in 0 until hs.length()) {
                val x = hs.getJSONObject(i)
                val oldId = x.optLong("id")
                val kind = x.optString("kind", "good")
                val schedule = x.optString("schedule", "daily")
                val created = if (x.has("createdDay")) parseDay(x.optString("createdDay")) else parseDay(firstEntry[oldId] ?: today.toString())
                val weekly = if (x.has("weeklyGoal")) x.optInt("weeklyGoal") else if (kind == "good" && schedule == "weekly") 3 else 0
                val h = Habit(
                    title = x.getString("title"), category = x.optString("category", "Мои привычки"), kind = kind,
                    note = x.optString("note", ""), favorite = x.optBoolean("favorite", false), archived = x.optBoolean("archived", false),
                    weeklyGoal = weekly, remindersOn = x.optBoolean("remindersOn", true), createdDay = created
                )
                val newId = insertHabitRow(db, h, schedule)
                ids[oldId] = newId
                if (version < 2 || oldId !in withRules) insertLegacyRules(db, newId, kind, schedule, today)
            }
            for (i in 0 until rs.length()) {
                val x = rs.getJSONObject(i)
                val newId = ids[x.optLong("habitId")] ?: continue
                insertRule(db, newId, x.getInt("dow"), x.optInt("minutes", -1), parseDay(x.getString("from")),
                    if (x.isNull("to")) null else parseDay(x.getString("to")))
            }
            for (i in 0 until es.length()) {
                val x = es.getJSONObject(i)
                val newId = ids[x.optLong("habitId")] ?: continue
                db.insertWithOnConflict("entries", null, ContentValues().apply {
                    put("habit_id", newId); put("day", x.getString("day")); put("status", x.getInt("status"))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (i in 0 until ns.length()) {
                val x = ns.getJSONObject(i)
                val newId = ids[x.optLong("habitId")] ?: continue
                db.insertWithOnConflict("day_notes", null, ContentValues().apply {
                    put("habit_id", newId); put("day", x.getString("day")); put("text", x.getString("text"))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
