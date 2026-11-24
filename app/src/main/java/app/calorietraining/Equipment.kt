package app.calorietraining

internal enum class Equipment(val label: String, val icon: Int) {
    Barbell("Штанга", R.drawable.ic_equipment_barbell),
    Dumbbell("Гантели", R.drawable.ic_equipment_dumbbell),
    Cable("Блок", R.drawable.ic_equipment_cable),
    Machine("Тренажёр", R.drawable.ic_equipment_machine),
    Bodyweight("Собственный вес", R.drawable.ic_equipment_bodyweight),
    Other("Другое", R.drawable.ic_equipment_other),
}
