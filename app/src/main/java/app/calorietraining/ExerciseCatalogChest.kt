package app.calorietraining

internal val chestCatalog = listOf(
    1 to ExercisePhoto(
        "Жим штанги лёжа", "Грудь", "жим лежа штанга",
        R.drawable.ex_barbell_bench_press_medium_grip_0, R.drawable.ex_barbell_bench_press_medium_grip_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Лягте на скамью и снимите штангу со стоек хватом средней ширины.",
                "На вдохе опустите гриф к середине груди.",
                "После короткой паузы выжмите штангу вверх на выдохе.",
                "Завершив подход, верните штангу на стойки.",
            ),
        ),
    ),
    2 to ExercisePhoto(
        "Жим гантелей лёжа", "Грудь", "гантели",
        R.drawable.ex_dumbbell_bench_press_0, R.drawable.ex_dumbbell_bench_press_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Лягте на горизонтальную скамью, помогая себе бёдрами поднять гантели.",
                "Разверните ладони вперёд и выжмите гантели над грудью.",
                "Медленно опустите гантели по сторонам груди и снова выжмите вверх.",
            ),
        ),
        dumbbell = true,
    ),
    3 to ExercisePhoto(
        "Жим гантелей на наклонной скамье", "Грудь", "гантели",
        R.drawable.ex_incline_dumbbell_press_0, R.drawable.ex_incline_dumbbell_press_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Поднимите гантели с бёдер и лягте на наклонную скамью.",
                "Выжмите гантели над грудью ладонями вперёд.",
                "Плавно опустите их по сторонам груди и повторите.",
            ),
        ),
        dumbbell = true,
    ),
    16 to ExercisePhoto(
        "Сведение рук в кроссовере", "Грудь", "кроссовер бабочка блоки",
        R.drawable.ex_cable_crossover_0, R.drawable.ex_cable_crossover_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders),
            listOf(
                "Возьмите рукояти верхних блоков, шагните вперёд и слегка наклонитесь.",
                "Сведите руки перед грудью по дуге, сохраняя небольшой сгиб в локтях.",
                "Плавно разведите руки, чтобы почувствовать растяжение груди.",
            ),
        ),
    ),
    17 to ExercisePhoto(
        "Жим штанги на наклонной скамье", "Грудь", "наклонная штанга верх груди",
        R.drawable.ex_barbell_incline_bench_press_medium_grip_0, R.drawable.ex_barbell_incline_bench_press_medium_grip_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Лягте на наклонную скамью и снимите штангу хватом средней ширины.",
                "Плавно опустите гриф к верхней части груди.",
                "После короткой паузы выжмите штангу вверх.",
                "Завершив подход, верните штангу на стойки.",
            ),
        ),
    ),
    18 to ExercisePhoto(
        "Разводка гантелей лёжа", "Грудь", "разводка гантели",
        R.drawable.ex_dumbbell_flyes_0, R.drawable.ex_dumbbell_flyes_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Chest), emptyList(),
            listOf(
                "Лягте на скамью и поднимите гантели над грудью ладонями друг к другу.",
                "Слегка согните локти и разведите руки по дуге до растяжения груди.",
                "Сведите гантели по той же дуге, сохраняя угол в локтях.",
            ),
        ),
        dumbbell = true,
    ),
    19 to ExercisePhoto(
        "Отжимания на брусьях на грудь", "Грудь", "собственный вес",
        R.drawable.ex_dips_chest_version_0, R.drawable.ex_dips_chest_version_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Примите упор на брусьях и наклоните корпус вперёд.",
                "Согните руки, позволяя локтям немного уйти в стороны.",
                "Когда почувствуете растяжение груди, выжмите себя обратно в упор.",
            ),
        ),
    ),
    20 to ExercisePhoto(
        "Отжимания от пола", "Грудь", "собственный вес",
        R.drawable.ex_pushups_0, R.drawable.ex_pushups_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Примите упор лёжа на ладонях и носках, вытянув тело в прямую линию.",
                "Согните локти и опустите грудь почти до пола.",
                "Оттолкнитесь ладонями и вернитесь в упор.",
            ),
        ),
    ),
    21 to ExercisePhoto(
        "Жим в тренажёре сидя", "Грудь", "тренажер хаммер",
        R.drawable.ex_leverage_chest_press_0, R.drawable.ex_leverage_chest_press_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Настройте сиденье так, чтобы рукояти были на уровне середины или нижней части груди.",
                "Прижмите спину к спинке и выжмите рукояти вперёд.",
                "После короткой паузы плавно верните рукояти.",
            ),
        ),
    ),
    57 to ExercisePhoto(
        "Жим гантелей на скамье с наклоном вниз", "Грудь", "жим на отрицательном наклоне",
        R.drawable.ex_decline_dumbbell_bench_press_0, R.drawable.ex_decline_dumbbell_bench_press_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
            listOf(
                "Зафиксируйте ноги и лягте на скамью с опущенным головным концом.",
                "Держите гантели по бокам груди, предплечья направьте вверх.",
                "Выжмите гантели над грудью и плавно верните их вниз.",
            ),
        ),
        dumbbell = true,
    ),
    58 to ExercisePhoto(
        "Разводка гантелей на наклонной скамье", "Грудь", "наклонная разводка наклонные разведения",
        R.drawable.ex_incline_dumbbell_flyes_0, R.drawable.ex_incline_dumbbell_flyes_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Chest), listOf(Muscle.Shoulders),
            listOf(
                "Лягте на наклонную скамью и поднимите гантели над грудью.",
                "Слегка согните локти и разведите руки по дуге до комфортного растяжения груди.",
                "Сведите гантели над грудью по той же дуге, сохраняя угол в локтях.",
            ),
        ),
        dumbbell = true,
    ),
    59 to ExercisePhoto(
        "Сведение рук в тренажёре", "Грудь", "пек дек бабочка pec deck",
        R.drawable.ex_butterfly_0, R.drawable.ex_butterfly_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Chest), emptyList(),
            listOf(
                "Настройте сиденье так, чтобы рукояти были на уровне груди.",
                "Прижмите спину к опоре и плавно сведите руки перед грудью.",
                "Вернитесь в исходное положение, удерживая вес под контролем.",
            ),
        ),
    ),
)
