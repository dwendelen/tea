package se.daan.tea.web.model

import se.daan.tea.api.LocalDateTime
import se.daan.tea.api.daysBetween
import se.daan.tea.api.minusDays
import se.daan.tea.api.plusDays
import kotlin.math.ceil

fun calculate(application: Application, now: LocalDateTime): Calculation {
    val goalDate = plusDays(now, 90).copy(hour = 0, minute = 0)

    val lines = application.products
        .filter { !it.deprecated }
        .map {
        calculate(application, it, goalDate)
    }

    return Calculation(goalDate, lines)
}

private fun calculate(application: Application, productVersion: ProductVersion, goalDate: LocalDateTime): CalculationLine {
    val mostRecentMeasurements = application.measurements
        .map { it.date to it.measurements.firstOrNull { it.productVersion.id == productVersion.id }?.let{ total(it) } }
        .sortedByDescending { it.first }
        .dropWhile { it.second == null || it.second == 0 }

    if(mostRecentMeasurements.size < 2) {
        return CalculationLine(productVersion, null, null, 0, 0, 0, 0, 0, null, 0,0, 0, 1)
    }

    val end = mostRecentMeasurements.first()
    val endDate = end.first
    val cleanEndDate = endDate.copy(hour = 0, minute = 0)
    val oneMonthAgo = minusDays(cleanEndDate, 30)

    val mostRecentOneMonthMeasurements = mostRecentMeasurements
        .takeWhile { it.first >= oneMonthAgo && it.second != null && it.second != 0 }

    val start = mostRecentOneMonthMeasurements.last()
    val startDate = start.first

    val diff = start.second!! - end.second!! + 0
    val days = daysBetween(startDate, endDate)

    val goalDays = daysBetween(endDate, goalDate)

    val goal = ceil(diff.toDouble() / days.toDouble() * goalDays).toInt()
    val toOrder = goal - end.second!!
    val boxesToOrder = ceil(toOrder.toFloat() / productVersion.boxSize.toDouble()).toInt()

    return CalculationLine(
        productVersion,
        start.first,
        end.first,
        start.second!!,
        end.second!!,
        0,
        diff,
        days,
        goalDate,
        goalDays,
        goal,
        toOrder,
        boxesToOrder
    )
}

private fun total(productMeasurement: ProductMeasurementVersion): Int {
    return productMeasurement.tray +
            productMeasurement.boxes * productMeasurement.productVersion.boxSize +
            productMeasurement.loose
}

data class Calculation(
    val goalDate: LocalDateTime,
    val lines: List<CalculationLine>
)

data class CalculationLine(
    val productVersion: ProductVersion,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val amountStart: Int,
    val amountEnd: Int,
    val deltas: Int,
    val diff: Int,
    val days: Int,
    val goalDate: LocalDateTime?,
    val goalDays: Int,
    val goal: Int,
    val toOrder: Int,
    val boxesToOrder: Int,
) {

}
