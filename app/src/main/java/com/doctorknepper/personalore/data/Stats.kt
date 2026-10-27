package com.doctorknepper.personalore.data

import java.time.LocalDate

object Stats {
    fun days(entries: List<Entry>): Set<LocalDate> = entries.map { it.date }.toSet()

    /** Consecutive days with an entry, ending today (or yesterday, so the streak isn't lost before you write today). */
    fun currentStreak(entries: List<Entry>): Int {
        val d = days(entries)
        var day = LocalDate.now()
        if (day !in d) day = day.minusDays(1)
        var n = 0
        while (day in d) { n++; day = day.minusDays(1) }
        return n
    }

    fun longestStreak(entries: List<Entry>): Int {
        val sorted = days(entries).sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in sorted) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    fun wordCount(entries: List<Entry>) = entries.sumOf { e -> e.body.split(Regex("\\s+")).count { it.isNotBlank() } }

    /** Entries from this calendar day in earlier years. */
    fun onThisDay(entries: List<Entry>, today: LocalDate = LocalDate.now()) =
        entries.filter { it.date.year < today.year && it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth }

    /** Average mood per day for days that have a mood. */
    fun dailyMood(entries: List<Entry>): Map<LocalDate, Double> =
        entries.filter { it.mood != null }.groupBy { it.date }.mapValues { (_, v) -> v.mapNotNull { it.mood }.average() }
}
