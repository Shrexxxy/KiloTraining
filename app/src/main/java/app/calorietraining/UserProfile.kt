package app.calorietraining

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.LocalDate

internal data class UserProfile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Int,
    val activity: ActivityLevel,
    val kind: GoalKind,
)

internal data class OnboardingData(
    val profile: UserProfile?,
    val measurement: Measurement?,
    val hasGoals: Boolean,
)

internal fun SQLiteDatabase.createUserProfile() {
    execSQL("""CREATE TABLE user_profile (
        id INTEGER PRIMARY KEY CHECK(id=1),
        sex TEXT NOT NULL, birth_year INTEGER NOT NULL, height_cm INTEGER NOT NULL,
        activity TEXT NOT NULL, goal_kind TEXT NOT NULL, updated_at TEXT NOT NULL
    )""")
}

internal fun FoodStore.onboardingData(today: LocalDate): OnboardingData {
    val db = readableDatabase
    val profile = db.rawQuery("SELECT sex,birth_year,height_cm,activity,goal_kind FROM user_profile WHERE id=1", null)
        .use { row ->
            if (row.moveToFirst()) UserProfile(Sex.valueOf(row.getString(0)), row.getInt(1),
                row.getInt(2), ActivityLevel.valueOf(row.getString(3)), GoalKind.valueOf(row.getString(4)))
            else null
        }
    val measurement = db.rawQuery(
        "SELECT day,weight,fat FROM measurement WHERE day<=? AND weight!='' ORDER BY day DESC LIMIT 1",
        arrayOf(today.toString())
    ).use { row ->
        if (row.moveToFirst()) Measurement(LocalDate.parse(row.getString(0)), row.getString(1), row.getString(2))
        else null
    }
    val hasGoals = db.rawQuery("SELECT 1 FROM goal LIMIT 1", null).use { it.moveToFirst() }
    return OnboardingData(profile, measurement, hasGoals)
}

internal fun FoodStore.saveOnboarding(profile: UserProfile, measurement: Measurement, goals: DailyGoals) {
    val db = writableDatabase
    db.atomic {
        db.execSQL("""INSERT OR REPLACE INTO user_profile
            (id,sex,birth_year,height_cm,activity,goal_kind,updated_at) VALUES(1,?,?,?,?,?,?)""",
            arrayOf<Any>(profile.sex.name, profile.birthYear, profile.heightCm, profile.activity.name,
                profile.kind.name, Instant.now(clock).toString()))
        saveMeasurement(measurement)
        setGoals(measurement.day, goals)
    }
}

internal class OnboardingSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var skipped: Boolean
        get() = preferences.getBoolean("onboarding_skipped", false)
        set(value) { preferences.edit().putBoolean("onboarding_skipped", value).apply() }
}
