package app.calorietraining

internal val backCatalog = listOf(
    0 to ExercisePhoto(
        "Тяга вертикального блока", "Спина", "верхний блок спина",
        R.drawable.ex_widegrip_lat_pulldown_0, R.drawable.ex_widegrip_lat_pulldown_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack, Muscle.Shoulders),
            listOf(
                "Сядьте под верхним блоком, зафиксируйте бёдра и возьмитесь за гриф широким хватом сверху.",
                "Слегка отклонитесь назад и потяните гриф к верхней части груди, опуская локти.",
                "Плавно выпрямите руки, удерживая корпус неподвижно.",
            ),
        ),
    ),
    4 to ExercisePhoto(
        "Тяга горизонтального блока", "Спина", "сидя нижний блок",
        R.drawable.ex_seated_cable_rows_0, R.drawable.ex_seated_cable_rows_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
            listOf(
                "Сядьте, возьмите V-рукоять и упритесь в платформу, слегка согнув колени.",
                "Выпрямите корпус и подтяните рукоять к животу, ведя локти вдоль боков.",
                "Сведите лопатки и ненадолго замрите.",
                "Медленно выпрямите руки, сохраняя положение корпуса.",
            ),
        ),
    ),
    5 to ExercisePhoto(
        "Тяга гантели одной рукой", "Спина", "гантели в наклоне",
        R.drawable.ex_onearm_dumbbell_row_0, R.drawable.ex_onearm_dumbbell_row_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
            listOf(
                "Обопритесь ладонью и коленом на скамью, держите спину ровно.",
                "Свободной рукой потяните гантель к боку, не разворачивая корпус.",
                "Опустите гантель под контролем. Закончив повторения, смените руку.",
            ),
        ),
        dumbbell = true,
    ),
    6 to ExercisePhoto(
        "Подтягивания", "Спина", "турник собственный вес",
        R.drawable.ex_pullups_0, R.drawable.ex_pullups_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack),
            listOf(
                "Повисните на перекладине хватом сверху, выпрямив руки.",
                "Ведите локти вниз и подтяните верх груди к перекладине.",
                "Коротко задержитесь и плавно опуститесь в вис.",
            ),
        ),
    ),
    7 to ExercisePhoto(
        "Становая тяга", "Спина", "штанга",
        R.drawable.ex_barbell_deadlift_0, R.drawable.ex_barbell_deadlift_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.LowerBack), listOf(Muscle.Calves, Muscle.Forearms, Muscle.Glutes, Muscle.Hamstrings, Muscle.Lats, Muscle.MiddleBack, Muscle.Quadriceps, Muscle.Traps),
            listOf(
                "Присядьте к штанге, возьмитесь за гриф и выпрямите спину.",
                "Встаньте, одновременно разгибая колени и тазобедренные суставы.",
                "Отведите таз назад, согните колени и опустите штангу на пол.",
            ),
        ),
    ),
    22 to ExercisePhoto(
        "Тяга штанги в наклоне", "Спина", "штанга наклон",
        R.drawable.ex_bent_over_barbell_row_0, R.drawable.ex_bent_over_barbell_row_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
            listOf(
                "Возьмите штангу хватом сверху, слегка согните колени и наклоните корпус с ровной спиной.",
                "Потяните гриф к корпусу, ведя локти назад и сводя лопатки.",
                "Медленно опустите штангу, сохраняя наклон.",
            ),
        ),
    ),
    23 to ExercisePhoto(
        "Т-тяга", "Спина", "т-гриф тбар тяга в наклоне",
        R.drawable.ex_tbar_row_with_handle_0, R.drawable.ex_tbar_row_with_handle_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats),
            listOf(
                "Встаньте над закреплённым грифом и возьмите V-рукоять у блинов.",
                "Поставьте ноги широко, отведите таз назад и выпрямите руки.",
                "Подтяните вес к верхней части живота, сведя лопатки.",
                "После паузы плавно опустите вес.",
            ),
        ),
    ),
    24 to ExercisePhoto(
        "Тяга верхнего блока узким хватом", "Спина", "верхний блок узкий",
        R.drawable.ex_closegrip_front_lat_pulldown_0, R.drawable.ex_closegrip_front_lat_pulldown_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack, Muscle.Shoulders),
            listOf(
                "Сядьте под верхним блоком, зафиксируйте бёдра и возьмите гриф узким хватом сверху.",
                "Слегка отклоните корпус и потяните гриф к верхней части груди.",
                "Сведите лопатки, затем медленно выпрямите руки.",
            ),
        ),
    ),
    25 to ExercisePhoto(
        "Гиперэкстензия", "Спина", "поясница разгибание спины",
        R.drawable.ex_hyperextensions_back_extensions_0, R.drawable.ex_hyperextensions_back_extensions_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.LowerBack), listOf(Muscle.Glutes, Muscle.Hamstrings),
            listOf(
                "Упритесь бёдрами в подушку и закрепите стопы, оставив тазу свободу движения.",
                "Наклонитесь вперёд через тазобедренные суставы, сохраняя спину ровной.",
                "Поднимите корпус до прямой линии с ногами.",
            ),
        ),
    ),
    26 to ExercisePhoto(
        "Пуловер на блоке прямыми руками", "Спина", "широчайшие прямые руки",
        R.drawable.ex_straightarm_pulldown_0, R.drawable.ex_straightarm_pulldown_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.Lats), emptyList(),
            listOf(
                "Возьмите гриф верхнего блока широким хватом сверху и отступите назад.",
                "Слегка наклоните корпус и оставьте локти чуть согнутыми.",
                "По дуге опустите гриф к бёдрам и плавно поднимите обратно, сохраняя угол в локтях.",
            ),
        ),
    ),
    27 to ExercisePhoto(
        "Шраги с гантелями", "Спина", "трапеции шраги",
        R.drawable.ex_dumbbell_shrug_0, R.drawable.ex_dumbbell_shrug_1,
        Equipment.Dumbbell, ExerciseTechnique(
            listOf(Muscle.Traps), emptyList(),
            listOf(
                "Встаньте с гантелями в опущенных руках, ладони обращены к телу.",
                "Поднимите плечи к ушам, сохраняя руки прямыми.",
                "Задержитесь вверху и плавно опустите плечи.",
            ),
        ),
        dumbbell = true,
    ),
    60 to ExercisePhoto(
        "Подтягивания обратным хватом", "Спина", "обратный хват chin up",
        R.drawable.ex_chin_up_0, R.drawable.ex_chin_up_1,
        Equipment.Bodyweight, ExerciseTechnique(
            listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.Forearms, Muscle.MiddleBack),
            listOf(
                "Возьмитесь за перекладину ладонями к себе примерно на ширине плеч.",
                "Направьте локти вниз и подтянитесь, пока подбородок не окажется над перекладиной.",
                "Плавно опуститесь до выпрямления рук, избегая раскачивания.",
            ),
        ),
    ),
    61 to ExercisePhoto(
        "Тяга в тренажёре с упором грудью", "Спина", "хаммер рычажная тяга тяга с опорой груди",
        R.drawable.ex_leverage_iso_row_0, R.drawable.ex_leverage_iso_row_1,
        Equipment.Machine, ExerciseTechnique(
            listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack),
            listOf(
                "Настройте сиденье, упритесь грудью в подушку и возьмитесь за рукояти.",
                "Подтяните рукояти к корпусу, ведя локти назад и сводя лопатки.",
                "Плавно распрямите руки, сохраняя контакт груди с опорой.",
            ),
        ),
    ),
    62 to ExercisePhoto(
        "Тяга нижнего блока одной рукой", "Спина", "одноручная горизонтальная тяга сидя",
        R.drawable.ex_seated_one_arm_cable_pulley_rows_0, R.drawable.ex_seated_one_arm_cable_pulley_rows_1,
        Equipment.Cable, ExerciseTechnique(
            listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Traps),
            listOf(
                "Сядьте у нижнего блока, упритесь стопами в площадки и возьмите одну рукоять.",
                "Подтяните рукоять к боку, удерживая корпус неподвижно.",
                "Выпрямите руку под контролем. После подхода смените сторону.",
            ),
        ),
    ),
    63 to ExercisePhoto(
        "Шраги со штангой", "Спина", "трапеции подъём плеч",
        R.drawable.ex_barbell_shrug_0, R.drawable.ex_barbell_shrug_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Traps), emptyList(),
            listOf(
                "Встаньте прямо и держите штангу перед бёдрами на выпрямленных руках.",
                "Поднимите плечи к ушам, сохраняя руки прямыми.",
                "Коротко задержитесь и опустите плечи под контролем.",
            ),
        ),
    ),
    64 to ExercisePhoto(
        "Наклоны со штангой на плечах", "Спина", "good morning гуд морнинг",
        R.drawable.ex_good_morning_0, R.drawable.ex_good_morning_1,
        Equipment.Barbell, ExerciseTechnique(
            listOf(Muscle.Hamstrings), listOf(Muscle.Abdominals, Muscle.Glutes, Muscle.LowerBack),
            listOf(
                "Расположите штангу на верхней части спины и слегка согните колени.",
                "Отведите таз назад и наклоните корпус, сохраняя спину ровной.",
                "Разогнитесь за счёт движения таза вперёд и вернитесь в стойку.",
            ),
        ),
    ),
)
