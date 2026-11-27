package app.calorietraining

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h800dp")
class StepControlsTest {
    @get:Rule val ui = createComposeRule()
    private var steps by mutableIntStateOf(0)
    private var shown by mutableStateOf(true)

    private fun show() = ui.setContent {
        DiaryTheme(ThemeMode.DARK) {
            Box { if (shown) WeightScale("82,4", { steps++ }, enabled = true) }
        }
    }

    private val button get() = ui.onNodeWithContentDescription("Увеличить на 0,1")

    @Test fun fingerLeavingTheButtonStopsRepeat() {
        show()
        ui.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(1_000)
        assertTrue(steps > 1)
        button.performTouchInput { moveTo(Offset(-400f, -400f)) }
        ui.mainClock.advanceTimeByFrame()
        val atExit = steps
        ui.mainClock.advanceTimeBy(2_000)
        assertEquals(atExit, steps)
    }

    @Test fun closingWhileHeldStopsRepeat() {
        show()
        ui.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(1_000)
        assertTrue(steps > 1)
        ui.runOnIdle { shown = false }
        ui.mainClock.autoAdvance = true
        button.assertDoesNotExist()
        ui.mainClock.autoAdvance = false
        val atClose = ui.runOnIdle { steps }
        ui.mainClock.advanceTimeBy(2_000)
        assertEquals(atClose, steps)
    }

    @Test fun holdStartsAfterPauseThenRepeatsEvery90ms() {
        show()
        ui.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        ui.mainClock.advanceTimeBy(400)
        assertEquals(1, steps)
        ui.mainClock.advanceTimeBy(100)
        assertEquals(2, steps)
        ui.mainClock.advanceTimeBy(180)
        assertEquals(4, steps)
    }

    @Test fun stepRespectsWeightBounds() {
        assertEquals("82,5", "82,4".stepWeight(1))
        assertEquals("82,3", "82,4".stepWeight(-1))
        assertEquals("0", "0,05".stepWeight(-1))
        assertEquals("500", "499,95".stepWeight(1))
        assertEquals("0,1", "".stepWeight(1))
    }
}
