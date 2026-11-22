package app.calorietraining

import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class Sex(val label: String) {
    MALE("Мужской"), FEMALE("Женский")
}

internal enum class GoalKind(val label: String, val energyFactor: Double, val proteinPerKg: Double) {
    LOSE("Похудеть", 0.85, 2.0),
    MAINTAIN("Поддерживать вес", 1.0, 1.6),
    GAIN("Набрать массу", 1.10, 1.8)
}

internal enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTARY("Сидячая работа без тренировок", 1.2),
    LIGHT("1–3 тренировки в неделю", 1.375),
    MODERATE("3–5 тренировок", 1.55),
    HIGH("6–7 тренировок", 1.725),
    VERY_HIGH("Физическая работа и тренировки", 1.9)
}

internal data class GoalInput(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Int,
    val weightKg: Double,
    val bodyFat: Double?,
    val activity: ActivityLevel,
    val kind: GoalKind,
    val currentYear: Int,
)

internal data class GoalPlan(val goals: DailyGoals, val bmr: Double, val expenditure: Double, val formula: String)

internal fun calculateGoals(input: GoalInput): GoalPlan {
    val age = input.currentYear - input.birthYear
    val weight = input.weightKg
    val fat = input.bodyFat
    val male = input.sex == Sex.MALE
    val bmr = if (fat != null) 370 + 21.6 * weight * (1 - fat / 100)
        else 10 * weight + 6.25 * input.heightCm - 5 * age + if (male) 5 else -161
    val expenditure = bmr * input.activity.factor
    val target = expenditure * input.kind.energyFactor
    val bounded = if (input.kind == GoalKind.LOSE) maxOf(target, bmr, if (male) 1500.0 else 1200.0)
        else target
    val kcal = (bounded / 10).roundToInt() * 10
    val protein = (weight * input.kind.proteinPerKg).roundToInt()
    val fatGrams = maxOf(weight * 0.9, kcal * 0.2 / 9).roundToInt()
    val carbs = floor((kcal - 4 * protein - 9 * fatGrams) / 4.0).toInt().coerceAtLeast(0)
    val formula = if (fat != null) "Кэтча–МакАрдла" else "Миффлина–Сан Жеора"
    return GoalPlan(DailyGoals(kcal, MacroGoals(protein, fatGrams, carbs)), bmr, expenditure, formula)
}
