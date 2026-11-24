package app.calorietraining

internal val legsCatalog = listOf(
    8 to ExercisePhoto(
        "Приседания со штангой", "Ноги", "присед",
        R.drawable.ex_barbell_squat_0, R.drawable.ex_barbell_squat_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.LowerBack),
            listOf(
                "Снимите штангу со стоек, расположив гриф на верхней части спины.",
                "На вдохе согните колени и опустите таз, удерживая корпус собранным.",
                "Оттолкнитесь всей стопой от пола и встаньте на выдохе.",
                "После подхода верните штангу на стойки.",
            ),
        ),
    ),
    9 to ExercisePhoto(
        "Жим ногами", "Ноги", "тренажёр",
        R.drawable.ex_leg_press_0, R.drawable.ex_leg_press_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Сядьте, поставьте стопы на платформу на ширине плеч и снимите фиксаторы.",
                "Согните колени и медленно опустите платформу, сохраняя опору спиной.",
                "Выжмите платформу, оставляя колени слегка согнутыми. После подхода закройте фиксаторы.",
            ),
        ),
    ),
    10 to ExercisePhoto(
        "Разгибание ног сидя", "Ноги", "квадрицепс тренажёр",
        R.drawable.ex_leg_extensions_0, R.drawable.ex_leg_extensions_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Quadriceps), emptyList(),
            listOf(
                "Настройте валик над стопами и сядьте, прижав спину к опоре.",
                "Разогните колени и задержитесь вверху.",
                "Плавно опустите валик, сохраняя положение корпуса.",
            ),
        ),
    ),
    11 to ExercisePhoto(
        "Сгибание ног лёжа", "Ноги", "бицепс бедра тренажёр",
        R.drawable.ex_lying_leg_curls_0, R.drawable.ex_lying_leg_curls_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Hamstrings), emptyList(),
            listOf(
                "Лягте на живот, расположив валик у задней стороны голеней над пятками.",
                "Согните колени, не отрывая бёдра от скамьи.",
                "Медленно разогните ноги.",
            ),
        ),
    ),
    28 to ExercisePhoto(
        "Румынская тяга", "Ноги", "штанга бицепс бедра",
        R.drawable.ex_romanian_deadlift_0, R.drawable.ex_romanian_deadlift_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Hamstrings), listOf(Muscle.Calves, Muscle.Glutes, Muscle.LowerBack),
            listOf(
                "Возьмите штангу хватом сверху и встаньте, чуть согнув колени.",
                "Отведите таз назад и опустите гриф вдоль ног до натяжения задней стороны бёдер.",
                "Выпрямитесь, подавая таз вперёд.",
            ),
        ),
    ),
    29 to ExercisePhoto(
        "Выпады с гантелями", "Ноги", "выпады",
        R.drawable.ex_dumbbell_lunges_0, R.drawable.ex_dumbbell_lunges_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Встаньте прямо с гантелями по бокам и шагните вперёд.",
                "Согните оба колена, сохраняя корпус вертикальным.",
                "Оттолкнитесь передней стопой и вернитесь в стойку. Смените ногу.",
            ),
        ),
        dumbbell = true,
    ),
    30 to ExercisePhoto(
        "Болгарские сплит-приседания", "Ноги", "болгарские сплит гантели",
        R.drawable.ex_split_squat_with_dumbbells_0, R.drawable.ex_split_squat_with_dumbbells_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Поставьте заднюю стопу на скамью, переднюю вынесите вперёд и возьмите гантели.",
                "Согните переднюю ногу и опустите заднее колено к полу.",
                "Оттолкнитесь передней стопой и выпрямитесь. Затем смените ногу.",
            ),
        ),
        dumbbell = true,
        searchPhrase = "болгарские выпады",
    ),
    31 to ExercisePhoto(
        "Подъём на носки стоя", "Ноги", "икры голень тренажер",
        R.drawable.ex_standing_calf_raises_0, R.drawable.ex_standing_calf_raises_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Calves), emptyList(),
            listOf(
                "Настройте упоры под плечи и поставьте носки на платформу, оставив пятки свободными.",
                "Поднимите пятки максимально высоко, удерживая колени почти прямыми.",
                "Задержитесь и плавно опустите пятки ниже платформы.",
            ),
        ),
    ),
    32 to ExercisePhoto(
        "Подъём на носки сидя", "Ноги", "икры голень тренажер",
        R.drawable.ex_seated_calf_raise_0, R.drawable.ex_seated_calf_raise_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Calves), emptyList(),
            listOf(
                "Поставьте носки на платформу и настройте упор над коленями.",
                "Поднимите пятки и освободите фиксатор. Опустите пятки до растяжения икр.",
                "Поднимитесь на носки, задержитесь и плавно опуститесь.",
            ),
        ),
    ),
    33 to ExercisePhoto(
        "Ягодичный мост со штангой", "Ноги", "ягодицы хип траст мост",
        R.drawable.ex_barbell_hip_thrust_0, R.drawable.ex_barbell_hip_thrust_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Glutes), listOf(Muscle.Calves, Muscle.Hamstrings),
            listOf(
                "Сядьте перед скамьёй и расположите гриф на тазу, подложив мягкую накладку.",
                "Обопритесь верхом спины о скамью и прижмите стопы к полу.",
                "Поднимите таз, опираясь на пятки, и плавно опустите его обратно.",
            ),
        ),
    ),
    34 to ExercisePhoto(
        "Гоблет-присед", "Ноги", "присед гантель кубок",
        R.drawable.ex_goblet_squat_0, R.drawable.ex_goblet_squat_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.Shoulders),
            listOf(
                "Удерживайте гирю за дужку близко к груди.",
                "Присядьте между ног, сохраняя пятки на полу и грудь поднятой.",
                "Оттолкнитесь стопами и вернитесь в стойку.",
            ),
        ),
        dumbbell = true,
    ),
    35 to ExercisePhoto(
        "Гакк-присед", "Ноги", "гак тренажер присед",
        R.drawable.ex_hack_squat_0, R.drawable.ex_hack_squat_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Упритесь спиной и плечами в подушки, поставьте стопы на платформу и снимите фиксаторы.",
                "Медленно согните колени, не отрывая стопы от платформы.",
                "Разогните ноги, толкая платформу пятками, и после подхода верните фиксаторы.",
            ),
        ),
    ),
    36 to ExercisePhoto(
        "Сведение ног в тренажёре", "Ноги", "приводящие внутренняя поверхность бедра",
        R.drawable.ex_thigh_adductor_0, R.drawable.ex_thigh_adductor_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Adductors), listOf(Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Сядьте и поместите внутреннюю поверхность бёдер у упоров, возьмитесь за ручки.",
                "Плавно сведите ноги, сохраняя корпус неподвижным.",
                "После короткой паузы медленно разведите ноги.",
            ),
        ),
    ),
    37 to ExercisePhoto(
        "Разведение ног в тренажёре", "Ноги", "отводящие ягодицы",
        R.drawable.ex_thigh_abductor_0, R.drawable.ex_thigh_abductor_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Abductors), listOf(Muscle.Glutes),
            listOf(
                "Сядьте и прижмите наружную поверхность бёдер к упорам, возьмитесь за ручки.",
                "Разведите колени, удерживая корпус неподвижно.",
                "Выдержите паузу и плавно сведите ноги.",
            ),
        ),
    ),
    38 to ExercisePhoto(
        "Приседания в Смите", "Ноги", "смит тренажер присед",
        R.drawable.ex_smith_machine_squat_0, R.drawable.ex_smith_machine_squat_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.LowerBack),
            listOf(
                "Поставьте гриф на верх спины, возьмитесь двумя руками и снимите его с фиксаторов.",
                "Поставьте стопы примерно на ширине плеч и плавно присядьте.",
                "Оттолкнитесь пятками и выпрямитесь. В конце подхода снова зафиксируйте гриф.",
            ),
        ),
    ),
    65 to ExercisePhoto(
        "Фронтальные приседания", "Ноги", "присед со штангой на груди фронтальный присед",
        R.drawable.ex_front_squat_clean_grip_0, R.drawable.ex_front_squat_clean_grip_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Abdominals, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Уложите гриф на переднюю часть плеч и направьте локти вперёд.",
                "Согните колени и опуститесь, удерживая грудь и локти высоко.",
                "Оттолкнитесь всей стопой и встаньте, сохраняя положение грифа.",
            ),
        ),
    ),
    66 to ExercisePhoto(
        "Зашагивания на скамью с гантелями", "Ноги", "степ ап подъём на платформу",
        R.drawable.ex_dumbbell_step_ups_0, R.drawable.ex_dumbbell_step_ups_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Встаньте перед устойчивой платформой с гантелями вдоль тела.",
                "Поставьте всю стопу на платформу и поднимитесь за счёт опорной ноги.",
                "Контролируемо спуститесь и повторите с другой ногой.",
            ),
        ),
        dumbbell = true,
    ),
    67 to ExercisePhoto(
        "Сгибание ног сидя", "Ноги", "сидячий бицепс бедра",
        R.drawable.ex_seated_leg_curl_0, R.drawable.ex_seated_leg_curl_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Hamstrings), emptyList(),
            listOf(
                "Настройте спинку и валики так, чтобы колени совпали с осью тренажёра.",
                "Прижмите бёдра к сиденью и согните ноги, подтягивая валик под сиденье.",
                "Плавно выпрямите колени, удерживая вес под контролем.",
            ),
        ),
    ),
    68 to ExercisePhoto(
        "Становая тяга сумо", "Ноги", "широкая стойка sumo deadlift",
        R.drawable.ex_sumo_deadlift_0, R.drawable.ex_sumo_deadlift_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Hamstrings), listOf(Muscle.Adductors, Muscle.Forearms, Muscle.Glutes, Muscle.LowerBack, Muscle.MiddleBack, Muscle.Quadriceps, Muscle.Traps),
            listOf(
                "Поставьте стопы шире плеч, разверните носки наружу и возьмите гриф между коленями.",
                "Напрягите корпус и выпрямитесь, ведя гриф близко к ногам.",
                "Отведите таз, согните колени и опустите штангу на пол.",
            ),
        ),
    ),
    69 to ExercisePhoto(
        "Выпады со штангой", "Ноги", "выпад с грифом",
        R.drawable.ex_barbell_lunge_0, R.drawable.ex_barbell_lunge_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Расположите штангу на верхней части спины и выпрямитесь.",
                "Сделайте шаг вперёд и согните обе ноги, опуская заднее колено к полу.",
                "Оттолкнитесь передней стопой и вернитесь в исходную стойку.",
            ),
        ),
    ),
)
