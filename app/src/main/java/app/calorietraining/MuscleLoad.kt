package app.calorietraining

import java.math.BigDecimal
import java.time.LocalDate

internal data class MuscleLoad(val scores: Map<Muscle, BigDecimal>, val unknownSets: Int)

private val secondarySetScore = BigDecimal("0.5")
internal const val MUSCLE_LOAD_DAYS = 7L

internal fun muscleLoad(
    workouts: List<Workout>,
    today: LocalDate,
    technique: (String) -> ExerciseTechnique?,
): MuscleLoad {
    val week = today.minusDays(MUSCLE_LOAD_DAYS - 1)..today
    val scores = mutableMapOf<Muscle, BigDecimal>()
    var unknownSets = 0
    for (workout in workouts) {
        if (workout.day !in week) continue
        for (set in workout.sets) {
            if (set.planned || set.warmup) continue
            val muscles = technique(set.exercise)
            if (muscles == null) {
                unknownSets++
                continue
            }
            for (muscle in muscles.primary) {
                scores[muscle] = scores.getOrDefault(muscle, BigDecimal.ZERO) + BigDecimal.ONE
            }
            for (muscle in muscles.secondary) {
                scores[muscle] = scores.getOrDefault(muscle, BigDecimal.ZERO) + secondarySetScore
            }
        }
    }
    return MuscleLoad(scores, unknownSets)
}
