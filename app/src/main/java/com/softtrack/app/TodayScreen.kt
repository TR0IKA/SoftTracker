package com.softtrack.app

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru: Locale = Locale.forLanguageTag("ru")

private val phrases = listOf(
    "Небольшой шаг сегодня — ровный ритм завтра.",
    "Не идеально, а регулярно.",
    "Одна привычка за раз.",
    "Сделанное сегодня работает на тебя завтра.",
    "Спокойный темп тоже темп.",
    "Любая отметка — уже движение вперёд.",
    "Лучшее время для привычки — сейчас."
)

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Доброй ночи"
        h < 12 -> "Доброе утро"
        h < 18 -> "Добрый день"
        else -> "Добрый вечер"
    }
}

@Composable
fun TodayScreen(snap: Snapshot, today: LocalDate, p: Palette, a: Actions) {
    val active = snap.habits.filter { !it.archived }
    val good = active.filter { it.isGood && snap.isScheduled(it, today) }
    val bad = active.filter { !it.isGood && snap.isScheduled(it, today) }
    val list = good.sortedWith(compareBy<Habit>({ snap.isDone(it, today) }, { snap.reminderMinutes(it, today) ?: 1440 }, { it.title }))
    val done = good.count { snap.isDone(it, today) }
    val best = active.filter { it.isGood }.maxOfOrNull { snap.currentStreak(it, today) } ?: 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") { Hero(snap, today, p, done, good.size, best, Modifier.appear(0)) }
        item(key = "label") {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("План на сегодня", p, Modifier.weight(1f))
                Text("${good.size}", color = p.muted, fontSize = 12.sp)
            }
        }
        if (list.isEmpty()) {
            item(key = "empty") {
                if (active.isEmpty()) EmptyState("Пока здесь пусто", "Добавь первую привычку или выбери готовые из шаблонов.", p, actionLabel = "Выбрать из шаблонов", onAction = a.openTemplates)
                else EmptyState("На сегодня ничего не запланировано", "Дни вне расписания не считаются пропуском.", p)
            }
        }
        itemsIndexed(list, key = { _, h -> "g${h.id}" }) { i, h ->
            HabitRow(h, snap, today, p, a, Modifier.animateItem().then(if (i < 8) Modifier.appear(i + 1) else Modifier))
        }
        if (bad.isNotEmpty()) {
            item(key = "badlabel") { SectionLabel("Наблюдения", p, Modifier.padding(top = 8.dp)) }
            itemsIndexed(bad, key = { _, h -> "b${h.id}" }) { _, h ->
                ObservationRow(h, snap.status(h, today), p) { a.setStatus(h, today, it) }
            }
        }
    }
}

@Composable
private fun Hero(snap: Snapshot, today: LocalDate, p: Palette, done: Int, total: Int, best: Int, modifier: Modifier) {
    val frac = if (total == 0) 0f else done.toFloat() / total
    val count by animateIntAsState(done, label = "done-count")
    val phrase = when {
        total == 0 -> "Сегодня ничего не запланировано. Это не пропуск."
        done == total -> "На сегодня всё. Отличный ритм."
        else -> phrases[today.dayOfYear % phrases.size]
    }
    Panel(p, modifier, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(greeting(), color = p.muted, fontSize = 13.sp)
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).replaceFirstChar { it.uppercase() },
                    color = p.ink, fontSize = 21.sp, fontFamily = p.font, fontWeight = FontWeight.Medium, lineHeight = 26.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(phrase, color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            ProgressRing(frac, p, 104.dp, 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.Light, fontFamily = p.font)
                    Text("из $total", color = p.muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        WeekStrip(snap, today, p)
        if (best >= 2) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = p.accent, modifier = Modifier.size(16.dp))
                Text("Лучшая текущая серия: $best", color = p.ink, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun WeekStrip(snap: Snapshot, today: LocalDate, p: Palette) {
    val start = snap.weekStart(today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val (dn, sc) = snap.dayCounts(d)
            val ratio = if (sc == 0) 0f else dn.toFloat() / sc
            val isToday = d == today
            val past = !d.isAfter(today)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayShort[i], color = if (isToday) p.ink else p.muted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                val fill = if (sc > 0 && past) p.accent.copy(alpha = 0.18f + 0.82f * ratio) else Color.Transparent
                Column(
                    Modifier.size(26.dp)
                        .background(fill, if (p.editorial) RoundedCornerShape(2.dp) else CircleShape)
                        .border(if (isToday) 1.5.dp else 1.dp, if (isToday) p.ink else p.line, if (p.editorial) RoundedCornerShape(2.dp) else CircleShape),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sc > 0 && past) Text("$dn", color = if (ratio > 0.5f) p.accentInk else p.muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun HabitRow(h: Habit, snap: Snapshot, today: LocalDate, p: Palette, a: Actions, modifier: Modifier = Modifier) {
    val done = snap.isDone(h, today)
    val streak = snap.currentStreak(h, today)
    val (wd, wg) = snap.weekProgress(h, today)
    val time = snap.reminderMinutes(h, today)
    Panel(p, modifier, onClick = { a.open(h.id) }, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    h.title, color = if (done) p.muted else p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = p.font,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                Text(h.category, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                if (time != null || streak >= 2 || wg > 0) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (time != null) {
                            Icon(Icons.Default.Schedule, null, tint = p.muted, modifier = Modifier.size(13.dp))
                            Text(fmtTime(time), color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, end = 12.dp))
                        }
                        if (streak >= 2) {
                            Icon(Icons.Default.LocalFireDepartment, null, tint = p.accent, modifier = Modifier.size(13.dp))
                            Text("$streak", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, end = 12.dp))
                        }
                        if (wg > 0) Text("неделя $wd/$wg", color = p.muted, fontSize = 12.sp)
                    }
                }
            }
            CheckButton(done, p, { a.setStatus(h, today, if (done) 0 else 1) })
        }
    }
}

@Composable
private fun ObservationRow(h: Habit, status: Int?, p: Palette, onStatus: (Int) -> Unit) {
    Panel(p, pad = 14.dp) {
        Text(h.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = p.font)
        Text(h.category, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ObsButton("Без события", status == 1, p.accent, p) { onStatus(if (status == 1) 0 else 1) }
            ObsButton("Событие", status == -1, p.danger, p) { onStatus(if (status == -1) 0 else -1) }
        }
    }
}

@Composable
private fun ObsButton(label: String, selected: Boolean, color: Color, p: Palette, onClick: () -> Unit) {
    val shape = if (p.editorial) RoundedCornerShape(3.dp) else CircleShape
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = if (selected) p.accentInk else p.muted,
        modifier = Modifier
            .background(if (selected) color else Color.Transparent, shape)
            .border(1.dp, if (selected) color else p.line, shape)
            .bounceClick(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
