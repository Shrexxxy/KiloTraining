package app.calorietraining

internal val coreCatalog = listOf(
    53 to ExercisePhoto(
        "Скручивания", "Пресс", "лежа",
        R.drawable.ex_crunches_0, R.drawable.ex_crunches_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Abdominals), emptyList(),
            listOf(
                "Лягте на спину, согните колени и положите руки рядом с висками.",
                "На выдохе приподнимите плечи, оставляя поясницу на полу.",
                "Коротко задержитесь и медленно опустите плечи.",
            ),
        ),
    ),
    54 to ExercisePhoto(
        "Подъём ног в висе", "Пресс", "турник",
        R.drawable.ex_hanging_leg_raise_0, R.drawable.ex_hanging_leg_raise_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Abdominals), emptyList(),
            listOf(
                "Повисните на перекладине с выпрямленными руками и ногами.",
                "Подкрутите таз и поднимите ноги, не раскачиваясь.",
                "Ненадолго задержитесь и медленно опустите ноги.",
            ),
        ),
    ),
    55 to ExercisePhoto(
        "Планка", "Пресс", "статика кор",
        R.drawable.ex_plank_0, R.drawable.ex_plank_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Abdominals), emptyList(),
            listOf(
                "Обопритесь на носки и предплечья, расположив локти под плечами.",
                "Выстройте корпус и ноги в одну линию, напрягите живот.",
                "Удерживайте положение, не прогибаясь в пояснице.",
            ),
        ),
    ),
    56 to ExercisePhoto(
        "Скручивания на блоке", "Пресс", "блок молитва на коленях",
        R.drawable.ex_cable_crunch_0, R.drawable.ex_cable_crunch_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Abdominals), emptyList(),
            listOf(
                "Встаньте на колени перед верхним блоком и подтяните концы каната к лицу.",
                "Слегка согните тазобедренные суставы и скрутите корпус, ведя локти к бёдрам.",
                "Плавно разогните корпус, сохраняя положение таза.",
            ),
        ),
    ),
    78 to ExercisePhoto(
        "Русские скручивания", "Пресс", "русский твист russian twist",
        R.drawable.ex_russian_twist_0, R.drawable.ex_russian_twist_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Abdominals), listOf(Muscle.LowerBack),
            listOf(
                "Сядьте на пол, согните ноги и немного отклоните прямой корпус назад.",
                "Держите руки перед грудью и поверните корпус в сторону.",
                "Вернитесь через центр и повернитесь в другую сторону, удерживая таз неподвижно.",
            ),
        ),
    ),
    79 to ExercisePhoto(
        "Подъём ног лёжа", "Пресс", "поднимание ног на скамье",
        R.drawable.ex_flat_bench_lying_leg_raise_0, R.drawable.ex_flat_bench_lying_leg_raise_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Abdominals), emptyList(),
            listOf(
                "Лягте на спину на горизонтальную скамью и возьмитесь за её края.",
                "Поднимите почти прямые ноги до вертикали, удерживая корпус на опоре.",
                "Плавно опустите ноги до начала следующего повторения.",
            ),
        ),
    ),
)
