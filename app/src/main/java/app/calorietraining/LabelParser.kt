package app.calorietraining

import java.math.BigDecimal
import java.math.MathContext

internal enum class LabelNote(val description: String) {
    FROM_KJ("пересчитано из кДж"),
    FROM_PORTION("пересчитано с порции"),
    CORRECTED("исправлен символ"),
    REVIEW("требует проверки"),
}

internal sealed interface LabelBasis {
    data object Unknown : LabelBasis
    data object Per100g : LabelBasis
    data class Portion(val grams: BigDecimal) : LabelBasis
}

internal enum class LabelProblem(val description: String) {
    TRUNCATED("Разобраны только первые 4000 символов"),
    UNKNOWN_BASIS("Не указано, на какую массу приведены значения"),
    VOLUME("Значения на объём нельзя пересчитать на 100 г без плотности"),
    PORTION_WITHOUT_WEIGHT("Укажите массу порции в граммах"),
    AMBIGUOUS_COLUMNS("Не удалось определить колонку на 100 г"),
    INVALID_VALUE("Одно или несколько значений не удалось прочитать"),
    NO_VALUES("Пищевая ценность не распознана"),
    IMPOSSIBLE_MASS("Сумма БЖУ превышает массу продукта"),
    ENERGY_CONFLICT("Ккал и кДж не совпадают"),
    KJ_DISCARDED("кДж отброшены, ккал совпадают с расчётом по БЖУ"),
    ENERGY_MISMATCH("Калории не сходятся с БЖУ. Проверьте запятые"),
}

internal data class LabelValue(val value: BigDecimal, val note: LabelNote?)
internal data class LabelResult(
    val kcal: LabelValue?,
    val protein: LabelValue?,
    val fat: LabelValue?,
    val carbs: LabelValue?,
    val basis: LabelBasis,
    val problems: List<LabelProblem>,
)


private const val TEXT_LIMIT = 4000
internal val KJ_PER_KCAL = BigDecimal("4.184")
private val math = MathContext.DECIMAL128
private val half = BigDecimal("0.5")
private val spaces = Regex("""[\s\u00a0\u202f]++""")
private val brokenWord = Regex("""(?<=\p{L})[-\u00ad]\r?\n[ \t]*+(?=\p{L})""")
private val tokens = Regex("""[\p{L}\p{N}]++(?:[.,](?=[0-9озo])[0-9озo]++)?|[^\s]""")
private val joined = Regex("""([0-9озo]{1,24}(?:[.,][0-9озo]{1,24})?)(ккал|kcal|кдж|kj|г|g|мл|ml)""")
private val decimal = Regex("""[0-9озo]{1,24}(?:[.,][0-9озo]{1,24})?""")
private val group = Regex("""[0-9]{3}""")
private val groupedSpace = Regex("""(?<=[0-9])[\u00a0\u202f](?=[0-9]{3}(?![0-9]))""")

private enum class Nutrient(val kcal: String, val kj: String) {
    PROTEIN("4", "17"), FAT("9", "37"), CARBS("4", "17"),
    FIBER("2", "8"), POLYOLS("2.4", "10"), ERYTHRITOL("0", "0"), ALCOHOL("7", "29"),
    KCAL("0", "0"), KJ("0", "0"),
}

private val labels = mapOf(
    "белки" to Nutrient.PROTEIN, "белок" to Nutrient.PROTEIN,
    "protein" to Nutrient.PROTEIN, "proteins" to Nutrient.PROTEIN,
    "жиры" to Nutrient.FAT, "жир" to Nutrient.FAT, "fat" to Nutrient.FAT,
    "углеводы" to Nutrient.CARBS, "carbohydrates" to Nutrient.CARBS, "carbs" to Nutrient.CARBS,
    "волокна" to Nutrient.FIBER, "клетчатка" to Nutrient.FIBER, "fibre" to Nutrient.FIBER, "fiber" to Nutrient.FIBER,
    "полиолы" to Nutrient.POLYOLS, "полиолов" to Nutrient.POLYOLS, "polyols" to Nutrient.POLYOLS,
    "эритрит" to Nutrient.ERYTHRITOL, "erythritol" to Nutrient.ERYTHRITOL,
    "этанол" to Nutrient.ALCOHOL, "спирт" to Nutrient.ALCOHOL, "ethanol" to Nutrient.ALCOHOL,
    "ккал" to Nutrient.KCAL, "kcal" to Nutrient.KCAL, "кдж" to Nutrient.KJ, "kj" to Nutrient.KJ,
)
private val macros = listOf(Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS)
private val grams = setOf("г", "g")
private val volumes = setOf("мл", "ml")
private val portions = setOf("порцию", "порция", "порции", "serving", "portion")
private val fractions = setOf("насыщенные", "saturated", "сахара", "sugars")
private val invalidPrefix = setOf("-", "−", "–", "<", "≤", "менее", "less", "than", ">")
private val separators = setOf(":", "=", "отдельно")
private val dualColumn = listOf('│', '|', '\t')
private data class WeightColumn(val line: Int, val delimiter: Char, val index: Int)

private data class Reading(val value: BigDecimal, val corrected: Boolean = false) {
    val rounding: BigDecimal get() = half.scaleByPowerOfTen(-value.scale())
}

internal data class LabelEnergyEstimate(val kcal: BigDecimal, val rounding: BigDecimal)
internal data class LabelAnalysis(val result: LabelResult, val energy: LabelEnergyEstimate? = null)

internal fun parseLabel(text: String): LabelResult = analyzeLabel(text).result

internal fun analyzeLabel(text: String): LabelAnalysis {
    val problems = linkedSetOf<LabelProblem>()
    if (text.length > TEXT_LIMIT) problems += LabelProblem.TRUNCATED
    val bounded = brokenWord.replace(text.take(TEXT_LIMIT).lowercase(), "")
    val lines = bounded.lineSequence().toList()
    fun scan(input: String) = tokens.findAll(spaces.replace(groupedSpace.replace(input, ""), " ")).flatMap { match ->
        val split = joined.matchEntire(match.value)
        if (split == null) sequenceOf(match.value) else sequenceOf(split.groupValues[1], split.groupValues[2])
    }.toList()
    val words = scan(bounded)
    fun reject(problem: LabelProblem) =
        LabelAnalysis(LabelResult(null, null, null, null, LabelBasis.Unknown, (problems + problem).toList()))
    fun weightHeader(index: Int, units: Set<String>) =
        words[index] == "100" && words.getOrNull(index + 1) in units &&
            (index == 0 || words.getOrNull(index - 1) in setOf("на", "per", "в", "/"))
    val column = lines.withIndex().firstNotNullOfOrNull { (line, text) ->
        dualColumn.firstNotNullOfOrNull columnMatch@{ delimiter ->
            val cells = text.split(delimiter).map(::scan)
            if (cells.size !in 2..3) return@columnMatch null
            val basisCells = cells.takeLast(2)
            val weight = basisCells.indexOfFirst { cell ->
                cell.takeLast(2).let { it.firstOrNull() == "100" && it.lastOrNull() in grams }
            }
            if (weight >= 0 && basisCells[1 - weight].any { it in portions || it in volumes })
                WeightColumn(line, delimiter, weight) else null
        }
    }
    val per100 = column != null || words.indices.any { weightHeader(it, grams) } ||
        lines.any { line -> scan(line).let { it.size == 2 && it[0] == "100" && it[1] in grams } }
    val volume = words.indices.any { weightHeader(it, volumes) }
    val portionIndex = words.indexOfFirst { it in portions }
    val multiPreparation = ("сухого" in words || "dry" in words) &&
        ("приготовленного" in words || "prepared" in words)
    if (multiPreparation || (per100 && (volume || portionIndex >= 0) && column == null))
        return reject(LabelProblem.AMBIGUOUS_COLUMNS)
    val basis = when {
        per100 -> LabelBasis.Per100g
        volume -> return reject(LabelProblem.VOLUME)
        portionIndex >= 0 -> {
            val mass = listOf(portionIndex + 1, portionIndex + 2, portionIndex - 2).firstNotNullOfOrNull { index ->
                if (words.getOrNull(index + 1) in grams && words.getOrNull(index - 1) !in invalidPrefix)
                    readNumber(words.getOrNull(index), false)?.value?.takeIf { it.signum() > 0 }
                else null
            } ?: return reject(LabelProblem.PORTION_WITHOUT_WEIGHT)
            LabelBasis.Portion(mass)
        }
        else -> return reject(LabelProblem.UNKNOWN_BASIS)
    }
    val factor = if (basis is LabelBasis.Portion) BigDecimal(100).divide(basis.grams, math) else BigDecimal.ONE
    val content = if (column == null) words else lines.drop(column.line + 1).flatMap { line ->
        val cells = line.trim().split(column.delimiter)
        val selected = when {
            cells.size == 3 -> scan(cells[0]) + scan(cells[column.index + 1])
            cells.size == 2 -> {
                val value = scan(cells[column.index])
                val label = if (column.index == 1 && value.none { it in labels })
                    scan(cells[0]).filter { it in labels }.take(1) else emptyList()
                label + value
            }
            else -> emptyList()
        }
        selected + ";"
    }
    val fields = mutableMapOf<Nutrient, Reading>()
    val ambiguous = mutableSetOf<Nutrient>()
    var polyolsIncluded = false
    var erythritolInPolyols = false
    var erythritolInCarbs = false
    val nutritionBlock = "пищевая" in content || "nutrition" in content
    var ingredients = false
    for (index in content.indices) {
        if (content[index] in setOf("состав", "ingredients") && content.getOrNull(index + 1) == ":") {
            ingredients = true
            continue
        }
        val label = content[index]
        if ((label == "пищевая" && content.getOrNull(index + 1) == "ценность") ||
            (label == "nutrition" && content.getOrNull(index + 1) in setOf("facts", "information"))) ingredients = false
        if (ingredients) continue
        val nutrient = labels[label] ?: if (nutritionBlock && content.getOrNull(index + 1) == ":") {
            when (label) {
                "б" -> Nutrient.PROTEIN
                "ж" -> Nutrient.FAT
                "у" -> Nutrient.CARBS
                else -> null
            }
        } else null
        if (nutrient == null || content.getOrNull(index - 1) in fractions ||
            (nutrient == Nutrient.POLYOLS && content.getOrNull(index + 1) == "эритрит")) continue
        val energy = nutrient == Nutrient.KCAL || nutrient == Nutrient.KJ
        var next = index + 1
        while (content.getOrNull(next) in separators) next++
        val before = content.getOrNull(index - 1)
        val following = content.getOrNull(next + 1)
        val grouped = energy && before?.let(group::matches) == true &&
            content.getOrNull(index - 2)?.let { it.length in 1..3 && it.all(Char::isDigit) } == true
        val reading = when {
            energy && grouped -> null
            energy && content.getOrNull(index - 2) !in invalidPrefix ->
                readNumber(before, true) ?: readNumber(content.getOrNull(next), column == null)
                    ?.takeUnless { content.getOrNull(next + 1) in invalidPrefix ||
                        content.getOrNull(next + 1)?.let { token -> readNumber(token, false) } != null }
            energy -> null
            following in grams || column != null ->
                readNumber(content.getOrNull(next), following in grams)
            else -> null
        }
        if (reading == null) {
            problems += LabelProblem.INVALID_VALUE
            continue
        }
        if (fields[nutrient]?.value?.compareTo(reading.value)?.let { it != 0 } == true) ambiguous += nutrient
        fields[nutrient] = reading
        if (nutrient == Nutrient.POLYOLS) polyolsIncluded = content.getOrNull(index - 1) == "них"
        if (nutrient == Nutrient.ERYTHRITOL) {
            erythritolInPolyols = content.getOrNull(index - 1) in setOf("полиолов", "polyols")
            erythritolInCarbs = content.getOrNull(index - 1) == "них"
        }
    }
    if (ambiguous.isNotEmpty()) problems += LabelProblem.INVALID_VALUE
    ambiguous.forEach { fields.remove(it) }
    val lowerMass = macros.sumOf { nutrient ->
        fields[nutrient]?.let { (it.value - it.rounding).max(BigDecimal.ZERO) } ?: BigDecimal.ZERO
    } * factor
    if (lowerMass > BigDecimal(100) || macros.any { (fields[it]?.value ?: BigDecimal.ZERO) * factor > BigDecimal(100) }) {
        problems += LabelProblem.IMPOSSIBLE_MASS
        macros.forEach { fields.remove(it) }
    }

    fun model(kj: Boolean): Pair<BigDecimal, BigDecimal>? {
        if (macros.any { it !in fields }) return null
        val carb = fields.getValue(Nutrient.CARBS)
        val polyol = fields[Nutrient.POLYOLS]?.value ?: BigDecimal.ZERO
        val erythritol = fields[Nutrient.ERYTHRITOL]?.value ?: BigDecimal.ZERO
        val fiber = fields[Nutrient.FIBER]?.value ?: BigDecimal.ZERO
        val available = carb.value - (if (polyolsIncluded) polyol else BigDecimal.ZERO) -
            (if (erythritolInCarbs) erythritol else BigDecimal.ZERO)
        val otherPolyols = polyol - (if (erythritolInPolyols) erythritol else BigDecimal.ZERO)
        if (available.signum() < 0 || otherPolyols.signum() < 0) return null
        val components = fields.filterKeys { it != Nutrient.KCAL && it != Nutrient.KJ }
        fun coeff(nutrient: Nutrient) = BigDecimal(if (kj) nutrient.kj else nutrient.kcal)
        val estimate = components.entries.sumOf { (nutrient, reading) ->
            val value = when (nutrient) {
                Nutrient.CARBS -> available
                Nutrient.POLYOLS -> otherPolyols
                Nutrient.FIBER -> fiber
                else -> reading.value
            }
            value * coeff(nutrient)
        }
        val rounding = components.entries.sumOf { (nutrient, reading) ->
            val coefficient = when (nutrient) {
                Nutrient.POLYOLS -> if (polyolsIncluded) coeff(Nutrient.CARBS) - coeff(nutrient) else coeff(nutrient)
                Nutrient.ERYTHRITOL -> when {
                    erythritolInPolyols -> coeff(Nutrient.POLYOLS)
                    erythritolInCarbs -> coeff(Nutrient.CARBS)
                    else -> BigDecimal.ZERO
                }
                else -> coeff(nutrient)
            }
            reading.rounding * coefficient.abs()
        }
        return estimate to rounding
    }

    var kcal = fields[Nutrient.KCAL]
    var kj = fields[Nutrient.KJ]
    fun mismatch(reading: Reading, useKj: Boolean): Boolean? {
        val (expected, rounding) = model(useKj) ?: return null
        val floor = if (useKj) BigDecimal.TEN * KJ_PER_KCAL else BigDecimal.TEN
        return energyMismatch(reading.value * factor, expected * factor, (reading.rounding + rounding) * factor, floor)
    }

    if (kcal != null && kj != null) {
        val printed = kcal.value * factor
        val roughConflict = (printed - (kj.value * factor).divide(KJ_PER_KCAL, math)).abs() >
            BigDecimal(5).max(printed.movePointLeft(2) * BigDecimal(5))
        if (roughConflict && !(mismatch(kcal, false) == false && mismatch(kj, true) == false)) {
            if (mismatch(kcal, false) == false) problems += LabelProblem.KJ_DISCARDED
            else {
                problems += LabelProblem.ENERGY_CONFLICT
                kcal = null
            }
            kj = null
        }
    }
    val review = (if (kcal != null) mismatch(kcal, false) else kj?.let { mismatch(it, true) }) == true
    if (review) problems += LabelProblem.ENERGY_MISMATCH
    if (kcal == null && kj == null && macros.none { it in fields }) problems += LabelProblem.NO_VALUES

    fun result(reading: Reading?, fromKj: Boolean = false) = reading?.let {
        val amount = it.value * factor
        val note = when {
            review -> LabelNote.REVIEW
            it.corrected -> LabelNote.CORRECTED
            fromKj -> LabelNote.FROM_KJ
            basis is LabelBasis.Portion -> LabelNote.FROM_PORTION
            else -> null
        }
        LabelValue(if (fromKj) amount.divide(KJ_PER_KCAL, math) else amount, note)
    }

    val result = LabelResult(
        result(kcal) ?: result(kj, fromKj = true), result(fields[Nutrient.PROTEIN]),
        result(fields[Nutrient.FAT]), result(fields[Nutrient.CARBS]), basis, problems.toList(),
    )
    val energy = model(false)?.let { (estimate, rounding) ->
        LabelEnergyEstimate(estimate * factor, rounding * factor)
    }
    return LabelAnalysis(result, energy)
}
private fun readNumber(token: String?, correct: Boolean): Reading? {
    if (token == null || !decimal.matches(token) || token.none { it in '0'..'9' }) return null
    val corrections = token.count { it in "озo" }
    if (corrections > if (correct) 1 else 0) return null
    val value = token.replace(',', '.').replace('о', '0').replace('o', '0').replace('з', '3')
    return Reading(value.toBigDecimal(), corrections > 0)
}

internal val macroCoefficients = macros.map { BigDecimal(it.kcal) }

/** Калорийность по БЖУ в порядке «белки, жиры, углеводы»; без любого значения неизвестна. */
internal fun macroEnergy(values: List<BigDecimal?>): BigDecimal? =
    values.filterNotNull().takeIf { it.size == macroCoefficients.size }
        ?.zip(macroCoefficients)?.sumOf { (value, coefficient) -> value * coefficient }

internal fun macroEnergy(macros: Macros): BigDecimal? =
    macroEnergy(listOf(macros.protein, macros.fat, macros.carbs).map(String::amount))

internal fun energyMismatch(
    kcal: BigDecimal,
    estimate: BigDecimal,
    rounding: BigDecimal = BigDecimal.ZERO,
    floor: BigDecimal = BigDecimal.TEN,
): Boolean = (kcal - estimate).abs() > maxOf(floor, kcal.movePointLeft(1), rounding)
