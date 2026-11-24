package app.calorietraining

internal val armsCatalog = listOf(
    14 to ExercisePhoto(
        "Сгибание рук молотком", "Руки", "молотки молоточки бицепс гантели",
        R.drawable.ex_hammer_curls_0, R.drawable.ex_hammer_curls_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Biceps), emptyList(),
            listOf(
                "Встаньте с гантелями, развернув ладони к бёдрам.",
                "Прижмите локти к бокам и согните руки, сохраняя нейтральный хват.",
                "Сделайте короткую паузу и плавно опустите гантели.",
            ),
        ),
        dumbbell = true,
    ),
    15 to ExercisePhoto(
        "Разгибание рук на блоке с канатом", "Руки", "трицепс канатная рукоять сверху вниз",
        R.drawable.ex_triceps_pushdown_rope_attachment_0, R.drawable.ex_triceps_pushdown_rope_attachment_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Triceps), emptyList(),
            listOf(
                "Возьмите концы каната верхнего блока и прижмите локти к бокам.",
                "Разогните руки вниз, разводя концы каната возле бёдер.",
                "Плавно согните локти, удерживая плечи на месте.",
            ),
        ),
    ),
    44 to ExercisePhoto(
        "Подъём штанги на бицепс", "Руки", "бицепс штанга",
        R.drawable.ex_barbell_curl_0, R.drawable.ex_barbell_curl_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Biceps), listOf(Muscle.Forearms),
            listOf(
                "Возьмите штангу хватом снизу примерно на ширине плеч.",
                "Прижав локти к бокам, поднимите гриф, сгибая руки.",
                "После короткой паузы плавно разогните руки.",
            ),
        ),
    ),
    45 to ExercisePhoto(
        "Подъём гантелей на бицепс поочерёдно", "Руки", "бицепс гантели",
        R.drawable.ex_dumbbell_alternate_bicep_curl_0, R.drawable.ex_dumbbell_alternate_bicep_curl_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Biceps), listOf(Muscle.Forearms),
            listOf(
                "Встаньте с гантелями по бокам и прижмите локти к корпусу.",
                "Согните одну руку, разворачивая ладонь вверх.",
                "Плавно опустите гантель, верните ладонь к бедру и повторите другой рукой.",
            ),
        ),
        dumbbell = true,
    ),
    46 to ExercisePhoto(
        "Сгибание рук на нижнем блоке", "Руки", "бицепс блок кроссовер",
        R.drawable.ex_standing_biceps_cable_curl_0, R.drawable.ex_standing_biceps_cable_curl_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Biceps), emptyList(),
            listOf(
                "Возьмите прямую рукоять нижнего блока хватом снизу на ширине плеч.",
                "Удерживая локти у корпуса, согните руки и поднимите рукоять к плечам.",
                "После паузы плавно опустите рукоять.",
            ),
        ),
    ),
    47 to ExercisePhoto(
        "Сгибание рук на скамье Скотта", "Руки", "скотт бицепс",
        R.drawable.ex_preacher_curl_0, R.drawable.ex_preacher_curl_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Biceps), emptyList(),
            listOf(
                "Возьмите EZ-гриф узким хватом и положите плечи на подушку скамьи.",
                "Медленно опустите гриф, почти полностью разогнув руки.",
                "Поднимите гриф, сгибая локти, и задержитесь вверху.",
            ),
        ),
    ),
    48 to ExercisePhoto(
        "Разгибание рук на блоке с V-рукоятью", "Руки", "трицепс сверху вниз",
        R.drawable.ex_triceps_pushdown_vbar_attachment_0, R.drawable.ex_triceps_pushdown_vbar_attachment_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Triceps), emptyList(),
            listOf(
                "Возьмите V-рукоять верхнего блока хватом сверху и прижмите локти к бокам.",
                "Разогните руки так, чтобы рукоять опустилась к передней стороне бёдер.",
                "Выдержите короткую паузу и плавно согните руки.",
            ),
        ),
    ),
    49 to ExercisePhoto(
        "Разгибание из-за головы на блоке с канатом", "Руки", "трицепс кроссовер",
        R.drawable.ex_cable_rope_overhead_triceps_extension_0, R.drawable.ex_cable_rope_overhead_triceps_extension_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Triceps), emptyList(),
            listOf(
                "Возьмите канат нижнего блока, повернитесь спиной к блоку и вытяните руки над головой.",
                "Согните локти, опуская канат за голову и удерживая плечи неподвижно.",
                "Разогните руки вверх и ненадолго задержитесь.",
            ),
        ),
    ),
    50 to ExercisePhoto(
        "Французский жим EZ-штангой", "Руки", "трицепс лежа",
        R.drawable.ex_ezbar_skullcrusher_0, R.drawable.ex_ezbar_skullcrusher_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Triceps), listOf(Muscle.Forearms),
            listOf(
                "Лягте на скамью и поднимите EZ-гриф узким хватом над собой.",
                "Не двигая плечами, согните локти и опустите гриф к уровню лба.",
                "Плавно разогните руки и верните гриф наверх.",
            ),
        ),
    ),
    51 to ExercisePhoto(
        "Жим штанги узким хватом", "Руки", "трицепс лежа",
        R.drawable.ex_closegrip_barbell_bench_press_0, R.drawable.ex_closegrip_barbell_bench_press_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Triceps), listOf(Muscle.Chest, Muscle.Shoulders),
            listOf(
                "Лягте на скамью и снимите штангу хватом примерно на ширине плеч.",
                "Опустите гриф к середине груди, держа локти близко к корпусу.",
                "После паузы выжмите штангу вверх. В конце подхода верните её на стойки.",
            ),
        ),
    ),
    52 to ExercisePhoto(
        "Отжимания на брусьях на трицепс", "Руки", "собственный вес",
        R.drawable.ex_dips_triceps_version_0, R.drawable.ex_dips_triceps_version_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Triceps), listOf(Muscle.Chest, Muscle.Shoulders),
            listOf(
                "Примите упор на брусьях, удерживая корпус вертикально.",
                "Опускайтесь, ведя локти назад, пока они не согнутся примерно под прямым углом.",
                "Выпрямите руки и вернитесь в упор.",
            ),
        ),
    ),
    74 to ExercisePhoto(
        "Подъём EZ-штанги на бицепс", "Руки", "изогнутый гриф сгибание ez",
        R.drawable.ex_ez_bar_curl_0, R.drawable.ex_ez_bar_curl_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Biceps), emptyList(),
            listOf(
                "Возьмите изогнутый гриф хватом снизу и прижмите локти к бокам.",
                "Согните локти и поднимите гриф, сохраняя корпус неподвижным.",
                "Плавно опустите гриф до выпрямления рук.",
            ),
        ),
    ),
    75 to ExercisePhoto(
        "Концентрированный подъём на бицепс", "Руки", "концентрированное сгибание гантель",
        R.drawable.ex_concentration_curls_0, R.drawable.ex_concentration_curls_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Biceps), listOf(Muscle.Forearms),
            listOf(
                "Сядьте, расставьте ноги и уприте плечо рабочей руки во внутреннюю сторону бедра.",
                "Согните локоть и поднимите гантель к плечу.",
                "Контролируемо распрямите руку. После подхода смените сторону.",
            ),
        ),
        dumbbell = true,
    ),
    76 to ExercisePhoto(
        "Разгибание руки с гантелью в наклоне", "Руки", "кикбэк kickback трицепс",
        R.drawable.ex_tricep_dumbbell_kickback_0, R.drawable.ex_tricep_dumbbell_kickback_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Triceps), emptyList(),
            listOf(
                "Наклонитесь, обопритесь свободной рукой на скамью и поднимите локоть рабочей руки вдоль корпуса.",
                "Разогните руку в локте, сохраняя плечо неподвижным.",
                "Под контролем согните локоть и повторите, затем смените руку.",
            ),
        ),
        dumbbell = true,
    ),
    77 to ExercisePhoto(
        "Сгибание запястий со штангой", "Руки", "сгибание кистей хват снизу предплечья",
        R.drawable.ex_seated_palm_up_barbell_wrist_curl_0, R.drawable.ex_seated_palm_up_barbell_wrist_curl_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Forearms), emptyList(),
            listOf(
                "Сядьте, положите предплечья на бёдра и держите штангу ладонями вверх.",
                "Плавно опустите кисти, сохраняя предплечья на опоре.",
                "Согните кисти вверх и под контролем повторите движение.",
            ),
        ),
    ),
)
