package com.softtrack.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru: Locale = Locale.forLanguageTag("ru")

@Composable
fun CalendarScreen(snap: Snapshot, today: LocalDate, p: Palette, a: Actions) {
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val sel = parseDay(selected)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(p, pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset-- }) { Icon(Icons.Default.ChevronLeft, "Предыдущий месяц", tint = p.ink) }
                Text(
                    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", ru)).replaceFirstChar { it.uppercase() },
                    color = p.ink, fontSize = 18.sp, fontFamily = p.font, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).clickable { monthOffset = 0; selected = today.toString() },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { monthOffset++ }) { Icon(Icons.Default.ChevronRight, "Следующий месяц", tint = p.ink) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                dayShort.forEach { Text(it, Modifier.weight(1f), color = p.muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    if (targetState.isAfter(initialState)) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                    else (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                },
                label = "month"
            ) { m ->
                val first = m.atDay(1)
                val lead = first.dayOfWeek.value - 1
                val rows = (lead + m.lengthOfMonth() + 6) / 7
                Column(Modifier.padding(top = 6.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0..6) {
                                val idx = r * 7 + c - lead
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                    if (idx in 0 until m.lengthOfMonth()) {
                                        val d = first.plusDays(idx.toLong())
                                        DayBubble(d, today, d == sel, snap, p) { selected = d.toString() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        DayDetails(sel, snap, today, p, a)
    }
}

@Composable
private fun DayBubble(d: LocalDate, today: LocalDate, isSel: Boolean, snap: Snapshot, p: Palette, onClick: () -> Unit) {
    val (done, sched) = snap.dayCounts(d)
    val past = !d.isAfter(today)
    val ratio = if (sched == 0) 0f else done.toFloat() / sched
    val shape = if (p.editorial) RoundedCornerShape(3.dp) else CircleShape
    val bg = when {
        sched > 0 && past && done == sched -> p.accent
        sched > 0 && past && done > 0 -> p.accent.copy(alpha = 0.18f + 0.5f * ratio)
        sched > 0 && past -> p.danger.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val border = when {
        isSel -> p.ink
        d == today -> p.accent
        sched > 0 && !past -> p.line
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxSize().background(bg, shape).border(if (isSel || d == today) 1.5.dp else 1.dp, border, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("${d.dayOfMonth}", fontSize = 13.sp, color = if (sched > 0 && past && done == sched) p.accentInk else p.ink, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DayDetails(d: LocalDate, snap: Snapshot, today: LocalDate, p: Palette, a: Actions) {
    var noteFor by remember { mutableStateOf<Habit?>(null) }
    val scheduled = snap.habits.filter { snap.isScheduled(it, d) }
    val extra = snap.habits.filter { !snap.isScheduled(it, d) && snap.status(it, d) != null }
    val editable = !d.isAfter(today)
    val (done, sched) = snap.dayCounts(d)

    Panel(p, pad = 16.dp) {
        Text(d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).replaceFirstChar { it.uppercase() }, color = p.ink, fontSize = 17.sp, fontFamily = p.font, fontWeight = FontWeight.Medium)
        Text(
            when {
                scheduled.isEmpty() -> "В этот день ничего не запланировано"
                d.isAfter(today) -> "Запланировано: ${scheduled.size}"
                else -> "Выполнено $done из $sched"
            }, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        scheduled.forEach { h -> DayRow(h, d, today, snap, p, editable, a) { noteFor = h } }
        if (extra.isNotEmpty()) {
            Text("Вне расписания", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            extra.forEach { h -> DayRow(h, d, today, snap, p, editable, a) { noteFor = h } }
        }
    }
    noteFor?.let { h ->
        NoteDialog(p, h.title, snap.note(h, d), { a.saveNote(h, d, it); noteFor = null }, { noteFor = null })
    }
}

@Composable
private fun DayRow(h: Habit, d: LocalDate, today: LocalDate, snap: Snapshot, p: Palette, editable: Boolean, a: Actions, onNote: () -> Unit) {
    val st = snap.status(h, d)
    val note = snap.note(h, d)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { a.open(h.id) }) {
                Text(h.title, color = p.ink, fontSize = 15.sp, fontFamily = p.font)
                val label = when {
                    h.isGood && st == 1 -> "Выполнено"
                    h.isGood && d.isBefore(today) -> "Не выполнено"
                    h.isGood -> "Ожидает"
                    st == 1 -> "День без события"
                    st == -1 -> "Было событие"
                    else -> "Не отмечено"
                }
                Text(label, color = if (st == 1) p.accent else p.muted, fontSize = 12.sp)
            }
            IconButton(onClick = onNote, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, "Заметка", tint = if (note.isNotBlank()) p.accent else p.muted, modifier = Modifier.size(18.dp))
            }
            if (editable) {
                if (h.isGood) CheckButton(st == 1, p, { a.setStatus(h, d, if (st == 1) 0 else 1) })
                else Text(
                    if (st == 1) "без события" else if (st == -1) "событие" else "отметить", color = p.accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.setStatus(h, d, if (st == null) 1 else if (st == 1) -1 else 0) }.padding(8.dp)
                )
            }
        }
        if (note.isNotBlank()) Text(note, color = p.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
