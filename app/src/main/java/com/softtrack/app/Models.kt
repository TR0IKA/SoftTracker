package com.softtrack.app

import java.time.LocalDate
import java.util.Locale

val categories = listOf(
    "Уход за собой", "Здоровье и сон", "Спорт и осанка", "Зубы и улыбка",
    "Волосы и гигиена", "Стиль и внешний вид", "Саморазвитие", "Мои привычки"
)

val dayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val dayFull = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

/** Все старые привычки «ежедневно» действуют с этой даты — реальное начало истории задаёт created_day. */
val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)

data class Habit(
    val id: Long = 0,
    val title: String,
    val category: String,
    val kind: String = "good",
    val note: String = "",
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val weeklyGoal: Int = 0,
    val remindersOn: Boolean = true,
    val createdDay: LocalDate = LocalDate.now(),
    /** "" — без значка, эмодзи, либо "photo:<метка>" — своё фото в файле habit_<id>.jpg. */
    val icon: String = ""
) {
    val isGood: Boolean get() = kind == "good"
}

/**
 * Одна версия строки расписания. dow: 1 = Пн … 7 = Вс. minutes: -1 = без напоминания.
 * Правило действует в дни from <= d < to (to == null — действует сейчас).
 * Изменение расписания закрывает старые правила и открывает новые, поэтому история не переписывается.
 */
data class Rule(val habitId: Long, val dow: Int, val minutes: Int, val from: LocalDate, val to: LocalDate?)

data class RuleInput(val dow: Int, val minutes: Int)

fun fmtTime(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

fun scheduleText(rules: List<RuleInput>): String {
    val d = rules.map { it.dow }.distinct().sorted()
    return when {
        d.isEmpty() -> "Без расписания"
        d.size == 7 -> "Каждый день"
        else -> d.joinToString(" ") { dayShort[it - 1] }
    }
}
