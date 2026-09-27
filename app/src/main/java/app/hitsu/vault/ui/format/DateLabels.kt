package app.hitsu.vault.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.hitsu.vault.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** "21 sep 2026", with the month names spelled out in resources so they match the mockups. */
@Composable
fun dayLabel(date: LocalDate): String {
    val months = stringArrayResource(R.array.month_abbreviations)
    return stringResource(R.string.date_day, date.dayOfMonth, months[date.monthValue - 1], date.year)
}

/** "21 sep 2026 14:35" for the photo details. */
@Composable
fun dayTimeLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val time = "%02d:%02d".format(moment.hour, moment.minute)
    return stringResource(R.string.date_day_time, dayLabel(moment.toLocalDate()), time)
}
