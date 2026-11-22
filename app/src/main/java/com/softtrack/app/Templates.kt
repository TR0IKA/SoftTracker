package com.softtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Готовая привычка с расписанием. dow: 1 = Пн … 7 = Вс, minutes: -1 = без напоминания. */
data class HabitTemplate(val title: String, val category: String, val days: List<Int>, val minutes: Int = -1, val goal: Int = 0)

private val allDays = (1..7).toList()

val habitTemplates: List<HabitTemplate> = listOf(
    HabitTemplate("Умыться утром", "Уход за собой", allDays, 7 * 60 + 30),
    HabitTemplate("Умыться вечером", "Уход за собой", allDays, 22 * 60),
    HabitTemplate("Увлажняющий крем", "Уход за собой", allDays, 7 * 60 + 40),
    HabitTemplate("SPF при выходе на улицу", "Уход за собой", allDays, 8 * 60 + 30),
    HabitTemplate("Почистить зубы утром", "Зубы и улыбка", allDays, 7 * 60 + 35),
    HabitTemplate("Почистить зубы вечером", "Зубы и улыбка", allDays, 22 * 60 + 5),
    HabitTemplate("Использовать зубную нить", "Зубы и улыбка", allDays, 22 * 60 + 10),
    HabitTemplate("Спать 8–10 часов", "Здоровье и сон", allDays, 22 * 60 + 30),
    HabitTemplate("Погулять на свежем воздухе", "Здоровье и сон", allDays, 18 * 60),
    HabitTemplate("Пить воду в течение дня", "Здоровье и сон", allDays),
    HabitTemplate("Лёгкая разминка", "Спорт и осанка", allDays, 10 * 60),
    HabitTemplate("Занятие спортом", "Спорт и осанка", listOf(1, 3, 5), 18 * 60 + 30, goal = 3),
    HabitTemplate("Перерывы при долгом сидении", "Спорт и осанка", allDays),
    HabitTemplate("Следить за чистотой волос", "Волосы и гигиена", allDays),
    HabitTemplate("Поддерживать ногти в порядке", "Волосы и гигиена", listOf(7), 19 * 60),
    HabitTemplate("Подготовить одежду", "Стиль и внешний вид", allDays, 21 * 60 + 30)
)

private fun HabitTemplate.label(): String {
    val base = scheduleText(days.map { RuleInput(it, minutes) })
    return if (minutes >= 0) "$base · ${fmtTime(minutes)}" else base
}

@Composable
fun TemplatesScreen(
    snap: Snapshot, p: Palette,
    onAdd: (List<HabitTemplate>) -> Unit, onEdit: (HabitTemplate) -> Unit, onClose: () -> Unit
) {
    val existing = remember(snap) { snap.habits.map { it.title }.toSet() }
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть", tint = p.ink) }
            Column(Modifier.weight(1f)) {
                Text("Шаблоны привычек", color = p.ink, fontSize = 17.sp, fontFamily = p.font, fontWeight = FontWeight.Medium)
                Text("Отметь нужные. Карандаш открывает редактор расписания.", color = p.muted, fontSize = 11.sp)
            }
            Button(
                enabled = selected.isNotEmpty(), shape = p.shape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk),
                onClick = { onAdd(habitTemplates.filter { it.title in selected }) }
            ) { Text(if (selected.isEmpty()) "Добавить" else "Добавить (${selected.size})") }
            Spacer(Modifier.size(8.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val list = habitTemplates.filter { it.category == cat }
                if (list.isNotEmpty()) {
                    item(key = "cat-$cat") { SectionLabel(cat, p, Modifier.padding(top = 8.dp)) }
                    items(list, key = { it.title }) { t ->
                        val added = t.title in existing
                        val sel = t.title in selected
                        val toggle: () -> Unit = {
                            if (sel) {
                                selected.remove(t.title)
                            } else {
                                selected.add(t.title)
                            }
                        }
                        Panel(p, onClick = if (added) null else toggle, pad = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(24.dp).clip(p.checkShape)
                                        .background(if (sel) p.accent else Color.Transparent)
                                        .border(1.5.dp, if (sel || added) p.accent else p.muted.copy(alpha = 0.7f), p.checkShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sel) Icon(Icons.Default.Check, null, tint = p.accentInk, modifier = Modifier.size(16.dp))
                                    else if (added) Icon(Icons.Default.Check, null, tint = p.accent, modifier = Modifier.size(16.dp))
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(t.title, color = if (added) p.muted else p.ink, fontSize = 15.sp)
                                    Text(if (added) "Уже в списке" else t.label(), color = p.muted, fontSize = 12.sp)
                                }
                                if (!added) IconButton(onClick = { onEdit(t) }) { Icon(Icons.Default.Edit, "Изменить расписание", tint = p.muted, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
