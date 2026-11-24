package app.calorietraining

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LabelParserTest {
    @Test fun labelExamples() {
        check("белки 10 г, жиры 5 г, углеводы 20 г, 165 ккал", "165 10 5 20")
        check("Белки 3,5 г, жиры 2.0 г, углеводы 4,7 г, 51 ккал", "51 3.5 2 4.7")
        check("Энергетическая\nценность 165 ккал\nБелки\n10 г\nЖиры 5 г\nУглеводы 20 г", "165 10 5 20")
        verify("100 г │ порция 30 г\nБелки 10 │ 3 г\nЖиры 5 │ 1,5 г\nУглеводы 20 │ 6 г\nккал 165 │ 49,5", "165 10 5 20")
        val portion = verify("На порцию 25 г: 50 ккал, белки 2 г, жиры 2 г, углеводы 6 г", "200 8 8 24")
        assertTrue(portion.values().all { it?.note == LabelNote.FROM_PORTION })
        reject("На порцию: 50 ккал, белки 2 г, жиры 2 г, углеводы 6 г")
        reject("На 100 мл: 46 ккал, белки 0 г, жиры 0 г, углеводы 11,5 г")
        assertEquals(LabelNote.FROM_KJ, check("418,4 кДж, белки 5 г, жиры 0 г, углеводы 20 г", "100 5 0 20").kcal?.note)
        check("695 кДж / 165 ккал, белки 10 г, жиры 5 г, углеводы 20 г", "165 10 5 20")
        assertTrue(check("165 кДж / 165 ккал, белки 10 г, жиры 5 г, углеводы 20 г", "165 10 5 20").problems.isNotEmpty())
        assertEquals(LabelNote.CORRECTED, check("165 ккал, белки 1О г, жиры 5 г, углеводы 20 г", "165 10 5 20").protein?.note)
        assertEquals(LabelNote.CORRECTED, check("41 ккал, белки З,5 г, жиры 1 г, углеводы 4,5 г", "41 3.5 1 4.5").protein?.note)
        check("165 ккал, белки З г, жиры 5 г, углеводы 20 г", "165 - 5 20")
        check("120 ккал, белки 5 г, жиры 0 г, из них сахара 10 г", "120 5 0 -")
        reject("На 100 г и на порцию\nбелки 10 3 жиры 5 1,5 углеводы 20 6 ккал 165 49,5")
        check("белки 10 г, жиры 5 г, углеводы 20 г", "- 10 5 20")
        requiresReview("310 ккал, белки 10 г, жиры 5 г, углеводы 20 г", "310 10 5 20")
        assertNull(check("180 ккал, белки 10 г, жиры 4 г, углеводы 30 г, из них полиолы 10 г, из полиолов эритрит 5 г, волокна отдельно 6 г", "180 10 4 30").kcal?.note)
        assertNull(check("75 ккал, белки 0 г, жиры 0 г, углеводы 10 г, этанол 5 г", "75 0 0 10").kcal?.note)
        check("654 ккал, белки 50 г, жиры 30 г, углеводы 46 г", "654 - - -")
        check("20 ккал, белки <0,5 г, жиры 0 г, углеводы 5 г", "20 - 0 5")
    }

    @Test fun lostDecimalNeedsReview() {
        requiresReview("51 ккал, белки 35 г, жиры 2 г, углеводы 4,7 г", "51 35 2 4.7")
    }

    @Test fun localizationOrderAndRejection() {
        reject("")
        reject("Рецепт: смешать 100 г муки и 20 г масла")
        reject("Состав: сахар, белки молока")
        reject("На 100 г: состав: белки 10 г, жиры 5 г, углеводы 20 г")
        verify("Состав: молоко, белки 18 г\nПищевая ценность на 100 г: белки 10 г, жиры 5 г, углеводы 20 г, 165 ккал", "165 10 5 20")
        reject("165 ккал, белки 10 г, жиры 5 г, углеводы 20 г")
        verify("Per 100\u00a0g\nCarbohydrates\t20 g\nSugars 12 g\nFat\t5 g\nProtein\t10 g\n165 kcal", "165 10 5 20")
        check("165 ккал, жиры 5 г, из них сахара 12 г, Углево-\nды 20 г, белок 10 г", "165 10 5 20")
    }

    @Test fun keepsColumnGeometryAndRejectsAmbiguousNumbers() {
        verify("Пищевая ценность\n100 г\tпорция 30 г\nБелки\t10\t3 г\nЖиры\t5\t1,5 г\nУглеводы\t20\t6 г\nккал\t165\t49,5", "165 10 5 20")
        assertEquals(0, parseLabel("На 100 г: 1\u202f200 ккал").kcal?.value?.compareTo("1200".toBigDecimal()))
        check("1 200 кДж", "- - - -")
        verify("Порция 30 г │ 100 г\nБелки 3 │ 10 г\nЖиры 1,5 │ 5 г\nУглеводы 6 │ 20 г\nккал 49,5 │ 165", "165 10 5 20")
        check("белки 3-5 г, жиры -2 г, углеводы 4 г, -20 ккал", "- - - 4")
    }

    @Test fun noNumbersAndBoundedInput() {
        val random = Random(42)
        val words = listOf("белки", "О", "г", "ккал", "на", "порция", "жиры", "З", "углеводы")
        repeat(1000) { reject(List(20) { words.random(random) }.joinToString(" ")) }
        val start = System.nanoTime()
        val result = verify("На 100 г: 165 ккал".padEnd(4000) + "9", "165 - - -")
        assertTrue(result.problems.isNotEmpty())
        reject("На 100 г: " + "9".repeat(12000) + " ккал")
        assertTrue(System.nanoTime() - start < 200_000_000)
    }

    private fun requiresReview(body: String, expected: String) {
        assertTrue(check(body, expected).values().all { it?.note == LabelNote.REVIEW })
    }

    private fun reject(text: String) {
        assertTrue(verify(text, "- - - -").problems.isNotEmpty())
    }

    private fun check(body: String, expected: String) = verify("На 100 г: $body", expected)

    private fun verify(text: String, expected: String): LabelResult = parseLabel(text).also { result ->
        assertEquals(text, expected, result.values().joinToString(" ") {
            it?.value?.stripTrailingZeros()?.toPlainString() ?: "-"
        })
    }

    private fun LabelResult.values() = listOf(kcal, protein, fat, carbs)
}
