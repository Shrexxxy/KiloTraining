package app.calorietraining

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class WorkoutTemplate(val id: Long, val title: String)

internal fun createTemplateTables(db: SQLiteDatabase) {
    db.execSQL("ALTER TABLE lifting ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0")
    db.execSQL("CREATE TABLE workout_template(id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
    db.execSQL("""CREATE TABLE template_set(id INTEGER PRIMARY KEY, template INTEGER NOT NULL,
        exercise_group TEXT NOT NULL, exercise TEXT NOT NULL, weight TEXT NOT NULL,
        reps INTEGER NOT NULL, warmup INTEGER NOT NULL)""")
    db.execSQL("CREATE INDEX template_set_owner ON template_set(template)")
}

fun FoodStore.templates(): List<WorkoutTemplate> = readableDatabase.rawQuery(
    "SELECT id,title FROM workout_template ORDER BY id DESC", null
).use { rows ->
    buildList { while (rows.moveToNext()) add(WorkoutTemplate(rows.getLong(0), rows.getString(1))) }
}

fun FoodStore.saveTemplate(session: Long, title: String) {
    require(title.isNotBlank())
    val db = writableDatabase
    db.atomic {
        val id = db.insertOrThrow("workout_template", null, ContentValues().apply { put("title", title.trim()) })
        db.copyTemplateSets(id, session)
    }
}

private fun SQLiteDatabase.copyTemplateSets(id: Long, session: Long) {
    execSQL("""INSERT INTO template_set(template,exercise_group,exercise,weight,reps,warmup)
        SELECT ?,exercise_group,exercise,weight,reps,warmup FROM lifting WHERE session=? ORDER BY id""",
        arrayOf(id, session))
    rawQuery("SELECT 1 FROM template_set WHERE template=? LIMIT 1", arrayOf(id.toString())).use {
        if (!it.moveToFirst()) throw android.database.SQLException("Тренировка пуста")
    }
}

fun FoodStore.renameTemplate(id: Long, title: String) {
    require(title.isNotBlank())
    val values = ContentValues().apply { put("title", title.trim()) }
    if (writableDatabase.update("workout_template", values, "id=?", arrayOf(id.toString())) != 1) {
        throw android.database.SQLException("Шаблон не найден")
    }
}

fun FoodStore.replaceTemplate(id: Long, session: Long) {
    val db = writableDatabase
    db.atomic {
        val args = arrayOf(id.toString())
        if (db.delete("template_set", "template=?", args) == 0) {
            throw android.database.SQLException("Шаблон не найден")
        }
        db.copyTemplateSets(id, session)
    }
}

fun FoodStore.startTemplate(template: Long, date: LocalDate) {
    startTemplate(template) { newWorkout(date) }
}

internal fun FoodStore.startTemplate(template: Long, start: LocalDateTime): Long =
    startTemplate(template) { newWorkout(start, live = false) }

private fun FoodStore.startTemplate(template: Long, create: () -> Long): Long {
    val db = writableDatabase
    var session = 0L
    db.atomic {
        val title = db.rawQuery("SELECT title FROM workout_template WHERE id=?", arrayOf(template.toString())).use {
            if (!it.moveToFirst()) throw android.database.SQLException("Шаблон не найден")
            it.getString(0)
        }
        session = create()
        db.execSQL("UPDATE workout SET title=? WHERE id=?", arrayOf<Any>(title, session))
        db.execSQL("""INSERT INTO lifting(session,exercise_group,exercise,weight,reps,warmup,planned)
            SELECT ?,? || exercise_group,exercise,weight,reps,warmup,1
            FROM template_set WHERE template=? ORDER BY id""", arrayOf(session, UUID.randomUUID().toString(), template))
    }
    return session
}
