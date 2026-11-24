package app.calorietraining

internal val shouldersCatalog = listOf(
    12 to ExercisePhoto(
        "Жим гантелей сидя", "Плечи", "гантели вверх",
        R.drawable.ex_dumbbell_shoulder_press_0, R.drawable.ex_dumbbell_shoulder_press_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
            listOf(
                "Сядьте со спинкой и поднимите гантели к плечам, ладонями вперёд.",
                "Выжмите гантели вверх и коротко задержитесь.",
                "Плавно верните их на уровень плеч.",
            ),
        ),
        dumbbell = true,
    ),
    13 to ExercisePhoto(
        "Подъём гантелей в стороны", "Плечи", "махи разведения гантели",
        R.drawable.ex_side_lateral_raise_0, R.drawable.ex_side_lateral_raise_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), emptyList(),
            listOf(
                "Встаньте, опустив гантели по бокам ладонями к корпусу.",
                "Слегка согните локти и поднимите руки в стороны до уровня плеч.",
                "Медленно опустите гантели, удерживая корпус неподвижно.",
            ),
        ),
        dumbbell = true,
    ),
    39 to ExercisePhoto(
        "Армейский жим стоя", "Плечи", "штанга над головой",
        R.drawable.ex_standing_military_press_0, R.drawable.ex_standing_military_press_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
            listOf(
                "Возьмите штангу со стоек хватом сверху чуть шире плеч и удерживайте её у ключиц.",
                "Выжмите гриф над головой, сохраняя корпус собранным.",
                "Плавно опустите штангу к ключицам.",
            ),
        ),
    ),
    40 to ExercisePhoto(
        "Разведение гантелей в наклоне", "Плечи", "задняя дельта махи",
        R.drawable.ex_reverse_flyes_0, R.drawable.ex_reverse_flyes_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), emptyList(),
            listOf(
                "Лягте грудью на наклонную скамью, опустив гантели ладонями друг к другу.",
                "Слегка согните локти и разведите руки, сводя лопатки.",
                "Плавно опустите гантели, сохраняя сгиб в локтях.",
            ),
        ),
        dumbbell = true,
    ),
    41 to ExercisePhoto(
        "Подъём гантелей перед собой", "Плечи", "передняя дельта махи",
        R.drawable.ex_front_dumbbell_raise_0, R.drawable.ex_front_dumbbell_raise_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), emptyList(),
            listOf(
                "Встаньте с гантелями перед бёдрами, ладони к себе.",
                "Поднимите одну гантель перед собой чуть выше уровня плеча, слегка согнув локоть.",
                "Плавно опустите руку и повторите другой рукой.",
            ),
        ),
        dumbbell = true,
    ),
    42 to ExercisePhoto(
        "Тяга штанги к подбородку", "Плечи", "протяжка",
        R.drawable.ex_upright_barbell_row_0, R.drawable.ex_upright_barbell_row_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Traps),
            listOf(
                "Возьмите штангу хватом сверху чуть уже плеч и опустите к бёдрам.",
                "Ведите локти вверх и в стороны, подтягивая гриф близко к телу.",
                "После короткой паузы плавно опустите гриф.",
            ),
        ),
    ),
    43 to ExercisePhoto(
        "Тяга каната к лицу", "Плечи", "фейс пулл задняя дельта блок",
        R.drawable.ex_face_pull_0, R.drawable.ex_face_pull_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.MiddleBack),
            listOf(
                "Встаньте лицом к верхнему блоку и возьмите канат.",
                "Потяните канат к лицу, разводя кисти и удерживая плечи примерно параллельно полу.",
                "Медленно выпрямите руки.",
            ),
        ),
    ),
    70 to ExercisePhoto(
        "Жим Арнольда", "Плечи", "арнольд arnold press",
        R.drawable.ex_arnold_dumbbell_press_0, R.drawable.ex_arnold_dumbbell_press_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
            listOf(
                "Сядьте и поднимите гантели перед плечами ладонями к себе.",
                "Выжмите гантели вверх, постепенно разворачивая ладони вперёд.",
                "Опустите гантели и одновременно разверните ладони к себе.",
            ),
        ),
        dumbbell = true,
    ),
    71 to ExercisePhoto(
        "Подъём руки в сторону на блоке", "Плечи", "мах одной рукой нижний блок дельта",
        R.drawable.ex_standing_low_pulley_deltoid_raise_0, R.drawable.ex_standing_low_pulley_deltoid_raise_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Forearms),
            listOf(
                "Встаньте боком к нижнему блоку и возьмите рукоять дальней рукой.",
                "Слегка согните локоть и поднимите руку в сторону до уровня плеча.",
                "Плавно опустите руку перед корпусом. После подхода смените сторону.",
            ),
        ),
    ),
    72 to ExercisePhoto(
        "Жим в тренажёре на плечи", "Плечи", "рычажный жим вверх дельты",
        R.drawable.ex_leverage_shoulder_press_0, R.drawable.ex_leverage_shoulder_press_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
            listOf(
                "Настройте высоту сиденья так, чтобы рукояти начинали движение у плеч.",
                "Прижмите спину к опоре и выжмите рукояти вверх.",
                "Плавно опустите рукояти до исходного положения.",
            ),
        ),
    ),
    73 to ExercisePhoto(
        "Обратные разведения в тренажёре", "Плечи", "обратная бабочка задняя дельта пек дек",
        R.drawable.ex_reverse_machine_flyes_0, R.drawable.ex_reverse_machine_flyes_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Shoulders), emptyList(),
            listOf(
                "Сядьте лицом к спинке тренажёра и возьмите рукояти на уровне плеч.",
                "Слегка согните локти и разведите руки назад, сохраняя контакт с опорой.",
                "Плавно верните рукояти перед собой.",
            ),
        ),
    ),
)
