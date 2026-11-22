package com.softtrack.app

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru: Locale = Locale.forLanguageTag("ru")

@Composable
private fun TopBar(title: String, p: Palette, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = p.ink) }
        Text(title, color = p.ink, fontSize = 17.sp, fontFamily = p.font, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
private fun Tile(value: Int, label: String, p: Palette, modifier: Modifier, suffix: String = "") {
    val n by animateIntAsState(value, label = "tile")
    Panel(p, modifier, pad = 14.dp) {
        Text("$n$suffix", color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.Light, fontFamily = p.font)
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

@Composable
fun HabitDetail(
    h: Habit, snap: Snapshot, today: LocalDate, p: Palette, a: Actions,
    onBack: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    val rules = snap.lastRuleInputs(h)
    val cur = snap.currentStreak(h, today)
    val best = snap.longestStreak(h, today)
    val r30 = snap.rate(h, today.minusDays(29), today, today)
    val pct = if (r30.second == 0) 0 else r30.first * 100 / r30.second
    val (wd, wg) = snap.weekProgress(h, today)
    val obs = snap.observationCounts(h, today.minusDays(29), today)

    Column(Modifier.fillMaxSize()) {
        TopBar(h.category, p, onBack) {
            IconButton(onClick = { a.toggleFavorite(h) }) {
                Icon(if (h.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Избранное", tint = if (h.favorite) p.accent else p.muted)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Изменить", tint = p.ink) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(p, pad = 18.dp) {
                Text(h.title, color = p.ink, fontSize = 24.sp, fontFamily = p.font, fontWeight = FontWeight.Medium, lineHeight = 30.sp)
                if (h.note.isNotBlank()) Text(h.note, color = p.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
                if (h.isGood) {
                    Spacer(Modifier.height(14.dp))
                    Text(scheduleText(rules), color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (h.remindersOn && rules.any { it.minutes >= 0 } && !h.archived) {
                        Text(
                            rules.filter { it.minutes >= 0 }.joinToString("   ") { "${dayShort[it.dow - 1]} ${fmtTime(it.minutes)}" },
                            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(if (h.remindersOn) "Без напоминаний" else "Напоминания выключены", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    Text("Наблюдение: отмечай дни без события и дни с событием.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                if (h.archived) Text("Привычка в архиве", color = p.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }

            if (h.isGood) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(cur, "текущая серия", p, Modifier.weight(1f))
                    Tile(best, "рекорд серии", p, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(snap.totalDone(h), "выполнений всего", p, Modifier.weight(1f))
                    Tile(pct, "за 30 дней", p, Modifier.weight(1f), suffix = "%")
                }
                if (wg > 0) {
                    Panel(p, pad = 16.dp) {
                        Text("Цель на неделю: $wd из $wg", color = p.ink, fontSize = 14.sp)
                        LinearProgressIndicator(
                            progress = { (wd.toFloat() / wg).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(p.shape(6.dp)),
                            color = p.accent, trackColor = p.line
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(obs.first, "дней без события (30 дн.)", p, Modifier.weight(1f))
                    Tile(obs.second, "дней с событием (30 дн.)", p, Modifier.weight(1f))
                }
            }

            Panel(p, pad = 16.dp) {
                SectionLabel("Последние 4 недели", p)
                for (row in 0..3) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val d = today.minusDays((27 - (row * 7 + col)).toLong())
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                                DayCell(h, snap, d, today, p)
                            }
                        }
                    }
                }
                Text("Дни вне расписания не считаются пропуском.", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Panel(p, pad = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Заметки", p, Modifier.weight(1f))
                    TextButton(onClick = { noteDialog = true }) { Text("Заметка на сегодня", color = p.accent, fontSize = 12.sp) }
                }
                val notes = snap.notesOf(h).take(6)
                if (notes.isEmpty()) {
                    Text("Здесь можно записывать, как прошёл день или что помешало.", color = p.muted, fontSize = 13.sp)
                } else {
                    notes.forEach { (d, t) ->
                        Text(d.format(DateTimeFormatter.ofPattern("d MMMM", ru)), color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(t, color = p.ink, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 30.dp)) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f), shape = p.shape(50.dp)) {
                    Icon(if (h.archived) Icons.Default.Unarchive else Icons.Default.Archive, null, tint = p.ink)
                    Text(if (h.archived) "  Вернуть" else "  В архив", color = p.ink)
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), shape = p.shape(50.dp)) {
                    Icon(Icons.Default.Delete, null, tint = p.danger)
                    Text("  Удалить", color = p.danger)
                }
            }
        }
    }

    if (noteDialog) {
        NoteDialog(p, "Заметка на сегодня", snap.note(h, today), { a.saveNote(h, today, it); noteDialog = false }, { noteDialog = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = p.solid,
            title = { Text("Удалить привычку?", color = p.ink, fontFamily = p.font) },
            text = { Text("Вместе с ней удалятся история, расписание и заметки. Если нужно просто убрать её из списка, лучше отправить в архив.", color = p.muted) },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = p.danger, contentColor = p.accentInk)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = p.muted) } }
        )
    }
}

@Composable
private fun DayCell(h: Habit, snap: Snapshot, d: LocalDate, today: LocalDate, p: Palette) {
    val shape = if (p.editorial) RoundedCornerShape(2.dp) else CircleShape
    val scheduled = snap.isScheduled(h, d)
    val st = snap.status(h, d)
    val done = st == 1
    val missed = scheduled && !done && d.isBefore(today)
    val bg = when {
        done -> p.accent
        missed -> p.danger.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val border = when {
        d == today -> p.ink
        scheduled && !done && !missed -> p.accent.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Box(Modifier.fillMaxSize().background(bg, shape).border(1.dp, border, shape), contentAlignment = Alignment.Center) {
        if (!scheduled && !done) Box(Modifier.size(3.dp).background(p.line, CircleShape))
        else Text("${d.dayOfMonth}", color = if (done) p.accentInk else p.muted, fontSize = 10.sp)
    }
}

// ---------- редактор ----------

@Composable
fun HabitEditor(
    existing: Habit?, snap: Snapshot, today: LocalDate, p: Palette,
    onSave: (Habit, List<RuleInput>) -> Unit, onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: categories.first()) }
    var kind by remember { mutableStateOf(existing?.kind ?: "good") }
    var favorite by remember { mutableStateOf(existing?.favorite ?: false) }
    var remindersOn by remember { mutableStateOf(existing?.remindersOn ?: true) }
    var goal by remember { mutableStateOf(existing?.weeklyGoal ?: 0) }
    var catMenu by remember { mutableStateOf(false) }
    val days = remember {
        mutableStateMapOf<Int, Int>().apply {
            val src = if (existing != null) snap.lastRuleInputs(existing) else (1..7).map { RuleInput(it, -1) }
            src.forEach { put(it.dow, it.minutes) }
        }
    }
    val good = kind == "good"
    val canSave = title.isNotBlank() && (!good || days.isNotEmpty())
    val effGoal = minOf(goal, days.size)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть", tint = p.ink) }
            Text(if (existing == null) "Новая привычка" else "Изменить привычку", color = p.ink, fontSize = 17.sp, fontFamily = p.font, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Button(
                enabled = canSave, shape = p.shape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk),
                onClick = {
                    val inputs = if (good) days.map { RuleInput(it.key, it.value) }.sortedBy { it.dow } else (1..7).map { RuleInput(it, -1) }
                    onSave(
                        Habit(
                            id = existing?.id ?: 0, title = title.trim(), category = category, kind = kind, note = note.trim(),
                            favorite = favorite, archived = existing?.archived ?: false,
                            weeklyGoal = if (good) effGoal else 0, remindersOn = good && remindersOn,
                            createdDay = existing?.createdDay ?: today
                        ),
                        inputs
                    )
                }
            ) { Text("Сохранить") }
            Spacer(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(p) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(p), shape = p.shape(14.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Описание · необязательно") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), colors = fieldColors(p), shape = p.shape(14.dp))
            }
            Panel(p) {
                SectionLabel("Тип", p)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = good, onClick = { kind = "good" }, label = { Text("Полезная") }, shape = p.shape(50.dp), colors = chipColors(p))
                    FilterChip(selected = !good, onClick = { kind = "bad" }, label = { Text("Наблюдение") }, shape = p.shape(50.dp), colors = chipColors(p))
                }
                Spacer(Modifier.height(10.dp))
                SectionLabel("Категория", p)
                Box {
                    OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth(), shape = p.shape(14.dp)) {
                        Text(category, color = p.ink, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenu = false }) }
                    }
                }
            }
            if (good) {
                Panel(p) {
                    SectionLabel("Дни недели", p)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (dow in 1..7) {
                            val on = days.containsKey(dow)
                            Box(
                                Modifier.size(38.dp).clip(p.checkShape)
                                    .background(if (on) p.accent else p.panel2)
                                    .border(1.dp, if (on) p.accent else p.line, p.checkShape)
                                    .clickable {
                                        if (on) days.remove(dow) else days[dow] = days.values.firstOrNull { it >= 0 } ?: -1
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(dayShort[dow - 1], color = if (on) p.accentInk else p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                    if (days.isEmpty()) Text("Выбери хотя бы один день.", color = p.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    else Text("В остальные дни привычка не показывается и не считается пропуском.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Panel(p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Напоминания", color = p.ink, fontSize = 15.sp)
                            Text("Своё время для каждого дня", color = p.muted, fontSize = 12.sp)
                        }
                        Switch(checked = remindersOn, onCheckedChange = { remindersOn = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.accentInk, uncheckedTrackColor = p.panel2, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line))
                    }
                    if (remindersOn && days.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        days.keys.sorted().forEach { dow ->
                            val m = days[dow] ?: -1
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(dayFull[dow - 1], color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { pickTime(ctx, m) { days[dow] = it } }) {
                                    Text(if (m >= 0) fmtTime(m) else "Без времени", color = if (m >= 0) p.accent else p.muted, fontSize = 14.sp)
                                }
                                if (m >= 0) IconButton(onClick = { days[dow] = -1 }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Убрать время", tint = p.muted, modifier = Modifier.size(16.dp)) }
                                else Spacer(Modifier.size(32.dp))
                            }
                        }
                        if (days.size >= 2) {
                            TextButton(onClick = { pickTime(ctx, days.values.firstOrNull { it >= 0 } ?: -1) { t -> days.keys.toList().forEach { k -> days[k] = t } } }) {
                                Text("Одно время для всех дней", color = p.accent, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Panel(p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Цель на неделю", color = p.ink, fontSize = 15.sp)
                            Text(if (effGoal == 0) "Без цели" else "$effGoal из ${days.size} запланированных дней", color = p.muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { goal = maxOf(0, effGoal - 1) }) { Icon(Icons.Default.Remove, "Меньше", tint = p.ink) }
                        Text("$effGoal", color = p.ink, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { goal = minOf(days.size, effGoal + 1) }) { Icon(Icons.Default.Add, "Больше", tint = p.ink) }
                    }
                }
            }
            Panel(p) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("В избранное", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.accentInk, uncheckedTrackColor = p.panel2, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line))
                }
            }
            if (existing != null && good) {
                Text("Изменение дней и времени действует с сегодняшнего дня: прошлая история остаётся как была.", color = p.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
