package se.daan.tea.web.model

import se.daan.tea.api.*
import kotlin.math.ceil
import kotlin.math.floor

fun calculate2(application: Application, now: LocalDateTime): Calc2 {
    val goalDate = plusDays(now, 90).copy(hour = 0, minute = 0)

    val activeProducts = application.products.filter { it.status == ProductStatus.ACTIVE }
    val activeFlavours = activeProducts.map { it.flavour }.distinct()

    val total = calculate3(application, listOf(null), { m, _ -> total(m) }, { d, _ -> total(d) })
    val rate = total.items[0].diff.toDouble() / daysBetween(total.startDate, total.endDate).toDouble()
    val goalDays = daysBetween(total.endDate, goalDate)
    val goal = goalDays * rate

    val byFlavour = calculate3(application, activeFlavours, { m, f -> total(m, f)}, {d, f -> total(d, f)})
    val enrichedByFlavour = byFlavour.copy(items = byFlavour.items.map { fl ->
        fl.copy(goal = goal * fl.ratio)
    })

    val lastMeasurement = application.measurements.maxBy { it.date }

    val products = enrichedByFlavour.items.associate { fl ->
        val applicable = activeProducts.filter { it.flavour.id == fl.item.id }
        val calc3 = calculate3(application, applicable, { m, p -> total(m, p) }, { d, p -> total(d, p) })

        val enrichedCalc3Items = calc3.items.map { c3i ->
            val itemGoal = c3i.ratio * fl.goal!!

            val current = lastMeasurement.measurements
                .firstOrNull { it.productVersion.id == c3i.item.id }
                ?.let { total(it) }
                ?: 0

            val toOrder = itemGoal - current.toDouble()
            val boxesToOrder = toOrder / c3i.item.boxSize.toDouble()

            c3i.copy(
                goal = itemGoal,
                current = current,
                toOrder = toOrder,
                boxes = boxesToOrder
            )
        }

        fl.item to calc3.copy(items = enrichedCalc3Items)
    }

    val orders = products.values.flatMap { calc3 ->
        calc3.items.map { i ->
            val rounded = ceil(i.boxes!!).toInt()

            Order(i.item, rounded)
        }.filter { it.productVersion.supplierData != null && it.amount > 0 }
    }

    return Calc2(orders, goal, total, enrichedByFlavour, products)
}

fun <T> calculate3(
    application: Application,
    items: List<T>,
    totalM: (MeasurementVersion, T) -> Int,
    totalD: (DeltaVersion, T) -> Int
    ): Calc3<T> {
    val sorted = application.measurements
        .sortedByDescending { it.date }

    // Total stuff
    val sortedNotNull = sorted
        .map {
            val totals = items.map { fl ->
                ItemTotal(fl, totalM(it, fl))
            }
            DateTotal(it.date, totals)
        }
        .dropWhile { !it.totals.all{ t -> t.total != 0 } }
        .takeWhile { it.totals.all{ t -> t.total != 0 } }

    // TODO edge case
    val end = sortedNotNull.first()
    val oneMonthEarlier = minusDays(end.date, 30)

    val startIndex = sortedNotNull.indexOfFirst { it.date <= oneMonthEarlier }
    val correctedStartIndex = if(startIndex == -1) {
        sortedNotNull.size - 1
    } else {
        startIndex
    }
    // TODO edge case
    val start = sortedNotNull[correctedStartIndex]

    val diffs = items.mapIndexed { idx, fl ->
        val deltas = application.deltas
            .filter { it.date in start.date..end.date }
            .sumOf { totalD(it, fl) }
        val totalStart = start.totals[idx].total
        val totalEnd = end.totals[idx].total
        val diff = totalStart - totalEnd + deltas
        ItemDiff(fl, totalStart, deltas, totalEnd, diff)
    }

    val totalDiff = diffs.sumOf { it.diff }
    val resultItems = diffs.map {
        val ratio = it.diff.toDouble() / totalDiff.toDouble()
        Calc3Item(it.item, it.totalStart, it.deltas, it.totalEnd, it.diff, ratio, null, null, null, null)
    }

    return Calc3(start.date, end.date, resultItems)
}

data class DateTotal<T>(val date: LocalDateTime, val totals: List<ItemTotal<T>>)
data class ItemTotal<T>(val item: T, val total: Int)
data class ItemDiff<T>(val item: T, val totalStart: Int, val deltas: Int, val totalEnd: Int, val diff: Int)

data class Calc2(
    val orders: List<Order>,
    val goal: Double,
    val total: Calc3<Nothing?>,
    val flavours: Calc3<FlavourVersion>,
    val products: Map<FlavourVersion, Calc3<ProductVersion>>
)

data class Calc3<T>(
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val items: List<Calc3Item<T>>
)

data class Calc3Item<T>(
    val item: T,
    val totalStart: Int,
    val deltas: Int,
    val totalEnd: Int,
    val diff: Int,
    val ratio: Double,
    val goal: Double?,
    val current: Int?,
    val toOrder: Double?,
    val boxes: Double?,
)

fun calculate(application: Application, now: LocalDateTime): Calculation {
    val goalDate = plusDays(now, 90).copy(hour = 0, minute = 0)
    val lastMeasurement = application.measurements.maxByOrNull { it.date }

    val lines = application.products
        .filter { it.status == ProductStatus.ACTIVE }
        .map {
            calculate(application, it, goalDate, lastMeasurement)
        }

    val minOutOfStock = lines
        .mapNotNull { it.outOfStockDate }
        .minOrNull()

    return Calculation(minOutOfStock, goalDate, lastMeasurement, lines)
}

private fun calculate(application: Application, productVersion: ProductVersion, goalDate: LocalDateTime, lastMeasurement: MeasurementVersion?): CalculationLine {
    val mostRecentMeasurements = application.measurements
        .map { it.date to it.measurements.firstOrNull { it.productVersion.id == productVersion.id }?.let{ total(it) } }
        .sortedByDescending { it.first }
        .dropWhile { it.second == null || it.second == 0 }

    if(mostRecentMeasurements.size < 2) {
        return CalculationLine(productVersion, null, null, 0, 0, 0, 0, 0, null, 0,0, 0, 0,1, null, null)
    }

    val end = mostRecentMeasurements.first()
    val endDate = end.first
    val cleanEndDate = endDate.copy(hour = 0, minute = 0)
    val oneMonthAgo = minusDays(cleanEndDate, 30)

    val mostRecentOneMonthMeasurements = mostRecentMeasurements
        .takeWhile { it.first >= oneMonthAgo && it.second != null && it.second != 0 }

    val start = mostRecentOneMonthMeasurements.last()
    val startDate = start.first

    val deltas = application.deltas
        .filter { it.date in startDate..endDate }
        .sumOf { it.deltas.firstOrNull { it.productVersion.id == productVersion.id }?.let { total(it) } ?: 0 }

    val diff = start.second!! - end.second!! + deltas
    val days = daysBetween(startDate, endDate)

    val goalDays = daysBetween(lastMeasurement!!.date, goalDate)

    val current = lastMeasurement
        .measurements
        .first { it.productVersion.id == productVersion.id }
        .let { total(it) }

    val goal = ceil(diff.toDouble() / days.toDouble() * goalDays).toInt()
    val toOrder = goal - current
    val boxesToOrder = ceil(toOrder.toFloat() / productVersion.boxSize.toDouble()).toInt()
    val daysOutOfStock = floor(current * days.toDouble() / diff.toDouble()).toInt()
    val outOfStockDate = plusDays(lastMeasurement.date, daysOutOfStock)

    return CalculationLine(
        productVersion,
        start.first,
        end.first,
        start.second!!,
        end.second!!,
        deltas,
        diff,
        days,
        goalDate,
        goalDays,
        goal,
        current,
        toOrder,
        boxesToOrder,
        daysOutOfStock,
        outOfStockDate
    )
}

private fun total(productMeasurement: ProductMeasurementVersion): Int {
    return productMeasurement.tray +
            productMeasurement.boxes * productMeasurement.productVersion.boxSize +
            productMeasurement.loose
}

private fun total(productDelta: ProductDeltaVersion): Int {
    return productDelta.tray +
            productDelta.boxes * productDelta.productVersion.boxSize +
            productDelta.loose
}

private fun total(measurement: MeasurementVersion): Int {
    return measurement.measurements.sumOf { total(it) }
}

private fun total(delta: DeltaVersion): Int {
    return delta.deltas.sumOf { total(it) }
}

private fun total(measurement: MeasurementVersion, flavourVersion: FlavourVersion): Int {
    return measurement.measurements
        .filter { it.productVersion.flavour.id == flavourVersion.id }
        .sumOf { total(it) }
}

private fun total(delta: DeltaVersion, flavourVersion: FlavourVersion): Int {
    return delta.deltas
        .filter { it.productVersion.flavour.id == flavourVersion.id }
        .sumOf { total(it) }
}

private fun total(measurement: MeasurementVersion, productVersion: ProductVersion): Int {
    return measurement.measurements
        .filter { it.productVersion.id == productVersion.id }
        .sumOf { total(it) }
}

private fun total(delta: DeltaVersion, productVersion: ProductVersion): Int {
    return delta.deltas
        .filter { it.productVersion.id == productVersion.id }
        .sumOf { total(it) }
}

data class Calculation(
    val outOfStockDate: LocalDateTime?,
    val goalDate: LocalDateTime,
    val lastMeasurement: MeasurementVersion?,
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
    val current: Int,
    val toOrder: Int,
    val boxesToOrder: Int,
    val daysOutOfStock: Int?,
    val outOfStockDate: LocalDateTime?
) {

}

data class Order(
    val productVersion: ProductVersion,
    val amount: Int
)