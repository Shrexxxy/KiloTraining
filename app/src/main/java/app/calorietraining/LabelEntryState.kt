package app.calorietraining

import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal data class LabelEntryState(
    val perPortion: Boolean = false,
    val grams: String = "",
    val inKj: Boolean = false,
    val energy: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val notes: List<LabelNote?> = List(4) { null },
) {
    val entries get() = listOf(energy, protein, fat, carbs)
    val hasValues get() = entries.any(String::isNotBlank)
    private val factor get() = if (perPortion) grams.labelAmount()?.takeIf { it.signum() > 0 }
        ?.let { BigDecimal(100).divide(it, MathContext.DECIMAL128) } else BigDecimal.ONE
    val values get() = entries.mapIndexed { index, text ->
        val scale = factor
        text.labelAmount()?.let { amount ->
            scale?.let {
                val scaled = amount * it
                if (index == 0 && inKj) scaled.divide(KJ_PER_KCAL, MathContext.DECIMAL128) else scaled
            }
        }
    }
    val impossibleMass get() = values.drop(1).any { it != null && it > BigDecimal(100) } ||
        (factor?.let { scale ->
            entries.drop(1).mapNotNull(String::labelAmount).sumOf { raw ->
                (raw - BigDecimal("0.5").scaleByPowerOfTen(-raw.scale())).max(BigDecimal.ZERO)
            } * scale > BigDecimal(100)
        } ?: false)
    val valid get() = values[0] != null && !impossibleMass &&
        entries.drop(1).all { it.isBlank() || it.labelAmount() != null }
    private val manualEstimate get() = macroEnergy(values.drop(1))
    fun estimate(analysis: LabelAnalysis?): BigDecimal? = matchingEstimate(analysis)?.kcal ?: manualEstimate

    fun warning(analysis: LabelAnalysis?): Boolean = estimate(analysis)?.let { expected ->
        val kcal = values[0] ?: return false
        val scale = factor ?: return false
        val energyStep = BigDecimal("0.5").scaleByPowerOfTen(-requireNotNull(energy.labelAmount()).scale())
        val roundedEnergy = if (inKj) energyStep.divide(KJ_PER_KCAL, MathContext.DECIMAL128) else energyStep
        val manualRounding = entries.drop(1).mapNotNull(String::labelAmount).zip(macroCoefficients).sumOf { (value, coefficient) ->
            BigDecimal("0.5").scaleByPowerOfTen(-value.scale()) * coefficient
        }
        val rounding = matchingEstimate(analysis)?.rounding ?: (manualRounding * scale)
        energyMismatch(kcal, expected, roundedEnergy * scale + rounding)
    } ?: false

    private fun matchingEstimate(analysis: LabelAnalysis?): LabelEnergyEstimate? {
        if (analysis == null || perPortion) return null
        val imported = analysis.result.fields().drop(1)
        if (imported.any { it == null || it.note == LabelNote.REVIEW }) return null
        return analysis.energy.takeIf {
            imported.zip(entries.drop(1)).all { (value, entry) ->
                entry.labelAmount()?.compareTo(requireNotNull(value).value.formAmount().amount()) == 0
            }
        }
    }

    fun change(index: Int, value: String): LabelEntryState {
        val updated = notes.toMutableList().apply { this[index] = null }
        return when (index) {
            0 -> copy(energy = value, notes = updated)
            1 -> copy(protein = value, notes = updated)
            2 -> copy(fat = value, notes = updated)
            else -> copy(carbs = value, notes = updated)
        }
    }

    fun paste(result: LabelResult): LabelEntryState? {
        val parsed = result.fields().map { it?.takeUnless { value -> value.note == LabelNote.REVIEW } }
        if (parsed.all { it == null }) return this
        val current = values
        val mustConvert = perPortion || inKj
        if (mustConvert && entries.indices.any { index ->
            parsed[index] == null && entries[index].isNotBlank() && current[index] == null &&
                (perPortion || index == 0)
        }) return null
        val merged = parsed.mapIndexed { index, value ->
            value?.value?.formAmount() ?: if (perPortion || (inKj && index == 0)) {
                current[index]?.formAmount().orEmpty()
            } else entries[index]
        }
        return LabelEntryState(energy = merged[0], protein = merged[1], fat = merged[2], carbs = merged[3],
            notes = parsed.mapIndexed { index, value -> if (value == null) notes[index] else value.note })
    }
    fun applyTo(draft: FoodDraft): FoodDraft {
        val (kcal, protein, fat, carbs) = values.map { it?.formAmount().orEmpty() }
        return draft.copy(weighted = true, energy = kcal, macros = Macros(protein, fat, carbs))
    }

    companion object {
        val Saver = listSaver<LabelEntryState, String>(
            save = { listOf(it.perPortion.toString(), it.grams, it.inKj.toString()) + it.entries +
                it.notes.map { note -> note?.name.orEmpty() } },
            restore = {
                LabelEntryState(it[0].toBoolean(), it[1], it[2].toBoolean(), it[3], it[4], it[5], it[6],
                    it.drop(7).map { note -> note.takeIf(String::isNotEmpty)?.let(LabelNote::valueOf) })
            },
        )
    }
}

internal fun LabelResult.fields() = listOf(kcal, protein, fat, carbs)

internal fun BigDecimal.formAmount() = setScale(2, RoundingMode.HALF_UP).display()
private fun String.labelAmount() = takeIf { it.length <= 50 }?.amount()
