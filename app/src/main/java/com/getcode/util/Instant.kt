package com.getcode.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

fun Instant.toLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
    toLocalDateTime(timeZone).date

fun LocalDate.atStartOfDay(tz: TimeZone = TimeZone.currentSystemDefault()) = atStartOfDayIn(tz)

fun LocalDate.atEndOfDay(tz: TimeZone = TimeZone.currentSystemDefault()): Instant {
    val tomorrowAtMidnight = ((this + DatePeriod(days = 1)).atStartOfDayIn(tz))
    return tomorrowAtMidnight.minus(value = 1, unit = DateTimeUnit.NANOSECOND)
}