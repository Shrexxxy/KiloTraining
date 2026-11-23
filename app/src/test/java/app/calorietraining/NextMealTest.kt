package app.calorietraining

import org.junit.Assert.assertEquals
import org.junit.Test

class NextMealTest {
    @Test fun firstEmptyMainMealThenSnack() {
        assertEquals(Meal.BREAKFAST, nextMealToFill(emptyList()))
        assertEquals(Meal.BREAKFAST, nextMealToFill(listOf(Meal.UNSPECIFIED, Meal.SNACK)))
        assertEquals(Meal.LUNCH, nextMealToFill(listOf(Meal.BREAKFAST)))
        assertEquals(Meal.BREAKFAST, nextMealToFill(listOf(Meal.LUNCH)))
        assertEquals(Meal.DINNER, nextMealToFill(listOf(Meal.BREAKFAST, Meal.LUNCH)))
        assertEquals(Meal.SNACK, nextMealToFill(listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER)))
    }
}
