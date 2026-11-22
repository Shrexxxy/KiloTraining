package app.calorietraining

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment

internal class SkipOnboardingRule : TestWatcher() {
    override fun starting(description: Description) {
        OnboardingSettings(RuntimeEnvironment.getApplication()).skipped = true
    }
}
