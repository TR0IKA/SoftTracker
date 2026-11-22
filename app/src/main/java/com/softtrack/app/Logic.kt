package com.softtrack.app

import java.time.LocalDate

/**
 * Снимок всех данных. Вся логика расписаний, серий и статистики живёт здесь.
 * Главное правило: считаются только запланированные дни; незапланированный день никогда не пропуск.
 */
class Snapshot(
    val habits: List<Habit>,
    private val rules: Map<Long, List<Rule>>,
    private val entries: Map<Long, Map<LocalDate, Int>>,
    private val notes: Map<Long, Map<LocalDate, String>>
) {
    fun rulesOf(id: Long): List<Rule> = rules[id] ?: emptyList()

    fun activeRules(h: Habit, d: LocalDate): List<Rule> =
        rulesOf(h.id).filter { r -> r.dow == d.dayOfWeek.value && d >= r.from && (r.to == null || d < r.to) }

    fun isScheduled(h: Habit, d: LocalDate): Boolean = d >= h.createdDay && activeRules(h, d).isNotEmpty()

    fun status(h: Habit, d: LocalDate): Int? = entries[h.id]?.get(d)
    fun isDone(h: Habit, d: LocalDate): Boolean = status(h, d) == 1
    fun note(h: Habit, d: LocalDate): String = notes[h.id]?.get(d) ?: ""

    fun notesOf(h: Habit): List<Pair<LocalDate, String>> =
        (notes[h.id] ?: emptyMap()).entries
            .filter { it.value.isNotBlank() }
            .sortedByDescending { it.key }
            .map { it.key to it.value }

    fun reminderMinutes(h: Habit, d: LocalDate): Int? =
        activeRules(h, d).firstOrNull { it.minutes >= 0 }?.minutes

    /** Расписание, которое действует сейчас (или действовало последним, если привычка в архиве). */
    fun lastRuleInputs(h: Habit): List<RuleInput> {
        val all = rulesOf(h.id)
        val open = all.filter { it.to == null }
        val src = if (open.isNotEmpty()) open else {
            val last = all.mapNotNull { it.to }.maxOrNull()
            all.filter { it.to == last }
        }
        return src.map { RuleInput(it.dow, it.minutes) }.sortedBy { it.dow }
    }

    fun totalDone(h: Habit): Int = entries[h.id]?.values?.count { it == 1 } ?: 0

    fun currentStreak(h: Habit, today: LocalDate): Int {
        var n = 0
        if (isScheduled(h, today) && isDone(h, today)) n++
        var d = today.minusDays(1)
        while (d >= h.createdDay) {
            if (isScheduled(h, d)) {
                if (isDone(h, d)) n++ else break
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun longestStreak(h: Habit, today: LocalDate): Int {
        var best = 0
        var run = 0
        var d = h.createdDay
        while (!d.isAfter(today)) {
            if (isScheduled(h, d)) {
                if (isDone(h, d)) {
                    run++
                    if (run > best) best = run
                } else if (d.isBefore(today)) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** (выполнено, запланировано) за период. Сегодняшний день входит, только если уже выполнен. */
    fun rate(h: Habit, from: LocalDate, to: LocalDate, today: LocalDate): Pair<Int, Int> {
        var done = 0
        var sched = 0
        var d = from
        while (!d.isAfter(to) && !d.isAfter(today)) {
            if (isScheduled(h, d)) {
                if (isDone(h, d)) {
                    done++
                    sched++
                } else if (d.isBefore(today)) {
                    sched++
                }
            }
            d = d.plusDays(1)
        }
        return done to sched
    }

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    /** (выполнено на этой неделе, эффективная цель). Цель не может быть больше числа дней в расписании. */
    fun weekProgress(h: Habit, day: LocalDate): Pair<Int, Int> {
        val start = weekStart(day)
        var done = 0
        var sched = 0
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            if (isDone(h, d)) done++
            if (isScheduled(h, d)) sched++
        }
        val goal = if (h.weeklyGoal > 0) minOf(h.weeklyGoal, maxOf(sched, 1)) else 0
        return done to goal
    }

    /** По полезным привычкам: (выполнено, запланировано) в один день. */
    fun dayCounts(d: LocalDate): Pair<Int, Int> {
        var done = 0
        var sched = 0
        for (h in habits) {
            if (!h.isGood || !isScheduled(h, d)) continue
            sched++
            if (isDone(h, d)) done++
        }
        return done to sched
    }

    fun periodRate(from: LocalDate, to: LocalDate, today: LocalDate): Pair<Int, Int> {
        var done = 0
        var sched = 0
        for (h in habits) {
            if (!h.isGood) continue
            val r = rate(h, from, to, today)
            done += r.first
            sched += r.second
        }
        return done to sched
    }

    fun observationCounts(h: Habit, from: LocalDate, to: LocalDate): Pair<Int, Int> {
        var clean = 0
        var events = 0
        var d = from
        while (!d.isAfter(to)) {
            when (status(h, d)) {
                1 -> clean++
                -1 -> events++
            }
            d = d.plusDays(1)
        }
        return clean to events
    }
}
