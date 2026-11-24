package app.calorietraining

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNumbersTest {
    private val accents = DiaryAccents(
        kcal = Color.Green, protein = Color.Cyan, fat = Color.Magenta, carbs = Color.Yellow, workout = Color.Red,
    )

    @Test fun comparisonRoundsCaloriesToWholeNumbers() {
        assertEquals("было 2 114 ккал, стало 2 088 ккал (−26 ккал)",
            comparisonLabel("2113.75".toBigDecimal(), "2087.5".toBigDecimal(), " ккал", 0))
    }

    @Test fun comparisonRoundsWeightToTenths() {
        assertEquals("было 47,5 кг, стало 51,4 кг (+3,9 кг)", comparisonLabel("47.5".toBigDecimal(), "51.41".toBigDecimal(), " кг", 1))
    }

    @Test fun comparisonOfCountsHasNoUnit() {
        assertEquals("было 1, стало 8 (+7)", comparisonLabel(1.toBigDecimal(), 8.toBigDecimal(), "", 0))
        assertEquals("было —, стало 8", comparisonLabel(null, 8.toBigDecimal(), "", 0))
    }

    @Test fun macroGramsAreWhole() {
        assertEquals("131 / 140 г", macroAmount("130.58".toBigDecimal(), 140))
        assertEquals("72 г", macroAmount("71.83".toBigDecimal(), null))
        assertEquals("—", macroAmount(null, 65))
    }

    @Test fun normalBmiZoneUsesCalmAccent() {
        val zones = bmiZoneColors(accents, Color.Gray)
        assertEquals(4, zones.size)
        assertEquals(accents.kcal, zones[1])
        assertEquals(false, zones[1] == accents.fat || zones[1] == accents.workout)
    }
}
