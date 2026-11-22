package app.calorietraining

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalCalculationTest {
    @Test fun maintainsWeightWithMifflinStJeor() {
        val plan = calculateGoals(GoalInput(Sex.MALE, 1996, 180, 80.0, null,
            ActivityLevel.MODERATE, GoalKind.MAINTAIN, 2026))
        assertEquals(DailyGoals(2760, MacroGoals(128, 72, 400)), plan.goals)
    }

    @Test fun losesWeightWithMifflinStJeor() {
        val plan = calculateGoals(GoalInput(Sex.FEMALE, 2001, 165, 60.0, null,
            ActivityLevel.LIGHT, GoalKind.LOSE, 2026))
        assertEquals(DailyGoals(1570, MacroGoals(120, 54, 151)), plan.goals)
    }

    @Test fun gainsWeightWithKatchMcArdleWhenBodyFatIsKnown() {
        val plan = calculateGoals(GoalInput(Sex.MALE, 1996, 180, 80.0, 15.0,
            ActivityLevel.MODERATE, GoalKind.GAIN, 2026))
        assertEquals(DailyGoals(3140, MacroGoals(144, 72, 479)), plan.goals)
        assertEquals("Кэтча–МакАрдла", plan.formula)
    }

    @Test fun respectsCalorieFloor() {
        val plan = calculateGoals(GoalInput(Sex.FEMALE, 1966, 150, 45.0, null,
            ActivityLevel.SEDENTARY, GoalKind.LOSE, 2026))
        assertEquals(DailyGoals(1200, MacroGoals(90, 41, 117)), plan.goals)
    }
}
