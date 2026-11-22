package com.softtrack.app

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru: Locale = Locale.forLanguageTag("ru")

private fun pct(r: Pair<Int, Int>): Int = if (r.second == 0) 0 else r.first * 100 / r.second

@Composable
fun StatsScreen(snap: Snapshot, today: LocalDate, p: Palette) {
    var period by rememberSaveable { mutableStateOf(7) }
    val from = today.minusDays((period - 1).toLong())
    val prevTo = from.minusDays(1)
    val prevFrom = prevTo.minusDays((period - 1).toLong())
    val cur = snap.periodRate(from, today, today)
    val prev = snap.periodRate(prevFrom, prevTo, today)
    val curPct = pct(cur)
    val delta = curPct - pct(prev)
    val good = snap.habits.filter { it.isGood }

    val values = (period - 1 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        val (dn, sc) = snap.dayCounts(d)
        if (sc == 0) null else dn.toFloat() / sc
    }
    val labels = (period - 1 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        if (period == 7) d.format(DateTimeFormatter.ofPattern("EE", ru)) else if (d.dayOfMonth % 5 == 0) "${d.dayOfMonth}" else ""
    }

    val ranked = good.map { it to snap.rate(it, from, today, today) }.filter { it.second.second >= 2 }.sortedByDescending { pct(it.second) }
    val steady = ranked.take(3)
    val rare = ranked.filter { pct(it.second) < 100 }.sortedBy { pct(it.second) }.take(3)
    val streaks = good.filter { !it.archived }.map { it to snap.currentStreak(it, today) }.filter { it.second >= 2 }.sortedByDescending { it.second }.take(5)
    val records = good.map { it to snap.longestStreak(it, today) }.filter { it.second >= 2 }.sortedByDescending { it.second }.take(5)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30).forEach { n ->
                FilterChip(selected = period == n, onClick = { period = n }, label = { Text("$n дней", fontSize = 13.sp) }, shape = p.shape(50.dp), colors = chipColors(p))
            }
        }
        Panel(p, pad = 18.dp) {
            val shownPct by animateIntAsState(curPct, label = "pct")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$shownPct%", color = p.ink, fontSize = 46.sp, fontWeight = FontWeight.Light, fontFamily = p.font)
                Text("выполнено", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, bottom = 9.dp))
            }
            Text("${cur.first} из ${cur.second} запланированных", color = p.muted, fontSize = 13.sp)
            Text(
                if (prev.second == 0) "Для сравнения пока мало данных"
                else if (delta > 0) "На $delta п.п. лучше предыдущих $period дней (${pct(prev)}%)"
                else if (delta < 0) "На ${-delta} п.п. ниже предыдущих $period дней (${pct(prev)}%)"
                else "Так же, как в предыдущие $period дней (${pct(prev)}%)",
                color = if (delta > 0) p.accent else p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )
            LinearProgressIndicator(
                progress = { curPct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(p.shape(5.dp)),
                color = p.accent, trackColor = p.line
            )
        }
        Panel(p, pad = 16.dp) {
            SectionLabel("Динамика по дням", p)
            BarChart(values, labels, p)
            Text("Дни без запланированных привычек отмечены точкой и не считаются пропуском.", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (steady.isNotEmpty()) {
            Panel(p, pad = 16.dp) {
                SectionLabel("Самые стабильные", p)
                steady.forEach { (h, r) -> RateRow(h.title, r, p) }
            }
        }
        if (rare.isNotEmpty()) {
            Panel(p, pad = 16.dp) {
                SectionLabel("Выполняются реже", p)
                rare.forEach { (h, r) -> RateRow(h.title, r, p) }
            }
        }
        if (streaks.isNotEmpty()) {
            Panel(p, pad = 16.dp) {
                SectionLabel("Текущие серии", p)
                streaks.forEach { (h, n) -> LineItem(Icons.Default.LocalFireDepartment, h.title, "$n", p) }
            }
        }
        if (records.isNotEmpty()) {
            Panel(p, pad = 16.dp) {
                SectionLabel("Личные рекорды", p)
                records.forEach { (h, n) -> LineItem(Icons.Default.EmojiEvents, h.title, "$n", p) }
            }
        }
        if (ranked.isEmpty() && streaks.isEmpty()) {
            EmptyState("Пока мало данных", "Отмечай привычки несколько дней, и здесь появятся серии, рекорды и сравнение периодов.", p)
        }
        Text("Прогресс — это наблюдение за привычками, а не оценка тебя.", color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun RateRow(title: String, r: Pair<Int, Int>, p: Palette) {
    val v = pct(r)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(title, color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$v%  ·  ${r.first}/${r.second}", color = p.muted, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { v / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(p.shape(4.dp)),
            color = p.accent, trackColor = p.line
        )
    }
}

@Composable
private fun LineItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, p: Palette) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Text(title, color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(value, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
