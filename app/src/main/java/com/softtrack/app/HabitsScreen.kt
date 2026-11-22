package com.softtrack.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val filters = listOf("Все", "Полезные", "Наблюдения", "Избранные", "Архив")
private val sorts = listOf("По категории", "По названию", "По серии")

@Composable
fun HabitsScreen(snap: Snapshot, today: LocalDate, p: Palette, a: Actions) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Все") }
    var cat by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(0) }
    var catMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val q = query.trim()
    val shown = snap.habits.filter { h ->
        (if (filter == "Архив") h.archived else !h.archived) &&
            (filter != "Полезные" || h.isGood) &&
            (filter != "Наблюдения" || !h.isGood) &&
            (filter != "Избранные" || h.favorite) &&
            (cat.isEmpty() || h.category == cat) &&
            (q.isEmpty() || h.title.contains(q, ignoreCase = true) || h.note.contains(q, ignoreCase = true))
    }
    val sorted = when (sort) {
        1 -> shown.sortedBy { it.title.lowercase() }
        2 -> shown.sortedByDescending { snap.currentStreak(it, today) }
        else -> shown
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Поиск по названию и описанию", color = p.muted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = p.muted) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = p.muted) } },
            shape = p.shape(14.dp), colors = fieldColors(p)
        )
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            filters.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f, fontSize = 12.sp) },
                    shape = p.shape(50.dp), colors = chipColors(p))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { catMenu = true }) {
                    Text(if (cat.isEmpty()) "Все категории" else cat, color = p.muted, fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = p.muted)
                }
                DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                    DropdownMenuItem(text = { Text("Все категории") }, onClick = { cat = ""; catMenu = false })
                    categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { cat = c; catMenu = false }) }
                }
            }
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(sorts[sort], color = p.muted, fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = p.muted)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    sorts.forEachIndexed { i, s -> DropdownMenuItem(text = { Text(s) }, onClick = { sort = i; sortMenu = false }) }
                }
            }
        }
        if (sorted.isEmpty()) {
            EmptyState(
                if (filter == "Архив") "Архив пуст" else "Ничего не найдено",
                if (filter == "Архив") "Сюда попадают привычки, которые ты убрал из ежедневного списка." else "Попробуй другой фильтр или добавь привычку.", p
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
                itemsIndexed(sorted, key = { _, h -> h.id }) { i, h ->
                    HabitCard(h, snap, today, p, a, if (i < 8) Modifier.appear(i) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun HabitCard(h: Habit, snap: Snapshot, today: LocalDate, p: Palette, a: Actions, modifier: Modifier) {
    val streak = if (h.isGood) snap.currentStreak(h, today) else 0
    val (wd, wg) = snap.weekProgress(h, today)
    Panel(p, modifier, onClick = { a.open(h.id) }, pad = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(h.title, color = if (h.archived) p.muted else p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = p.font)
                Text(h.category, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(onClick = { a.toggleFavorite(h) }, modifier = Modifier.size(32.dp)) {
                Icon(if (h.favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (h.favorite) p.accent else p.muted, modifier = Modifier.size(20.dp))
            }
        }
        if (h.note.isNotBlank()) Text(h.note, color = p.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp), lineHeight = 18.sp)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (h.isGood) scheduleText(snap.lastRuleInputs(h)) else "Наблюдение", color = p.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (h.isGood && streak >= 2) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = p.accent, modifier = Modifier.size(14.dp))
                Text("$streak", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, end = 10.dp))
            }
            if (h.isGood) Text("всего ${snap.totalDone(h)}", color = p.muted, fontSize = 12.sp)
        }
        if (h.isGood && wg > 0 && !h.archived) {
            LinearProgressIndicator(
                progress = { (wd.toFloat() / wg).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(p.shape(4.dp)),
                color = p.accent, trackColor = p.line
            )
            Text("Неделя: $wd из $wg", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
