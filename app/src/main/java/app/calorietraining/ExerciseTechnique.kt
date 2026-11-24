package app.calorietraining

internal enum class Muscle(val label: String) {
    Abdominals("пресс"),
    Abductors("отводящие мышцы бедра"),
    Adductors("приводящие мышцы бедра"),
    Biceps("бицепс"),
    Calves("икры"),
    Chest("грудь"),
    Forearms("предплечья"),
    Glutes("ягодицы"),
    Hamstrings("бицепс бедра"),
    Lats("широчайшие"),
    LowerBack("поясница"),
    MiddleBack("середина спины"),
    Neck("шея"),
    Quadriceps("квадрицепс"),
    Shoulders("плечи"),
    Traps("трапеции"),
    Triceps("трицепс"),
}

internal data class ExerciseTechnique(
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val steps: List<String>,
)
