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

internal val exerciseTechniques = mapOf(
    R.drawable.ex_widegrip_lat_pulldown_0 to ExerciseTechnique(
        listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack, Muscle.Shoulders),
        listOf(
            "Сядьте под верхним блоком, зафиксируйте бёдра и возьмитесь за гриф широким хватом сверху.",
            "Слегка отклонитесь назад и потяните гриф к верхней части груди, опуская локти.",
            "Плавно выпрямите руки, удерживая корпус неподвижно.",
        ),
    ),
    R.drawable.ex_barbell_bench_press_medium_grip_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Лягте на скамью и снимите штангу со стоек хватом средней ширины.",
            "На вдохе опустите гриф к середине груди.",
            "После короткой паузы выжмите штангу вверх на выдохе.",
            "Завершив подход, верните штангу на стойки.",
        ),
    ),
    R.drawable.ex_dumbbell_bench_press_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Лягте на горизонтальную скамью, помогая себе бёдрами поднять гантели.",
            "Разверните ладони вперёд и выжмите гантели над грудью.",
            "Медленно опустите гантели по сторонам груди и снова выжмите вверх.",
        ),
    ),
    R.drawable.ex_incline_dumbbell_press_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Поднимите гантели с бёдер и лягте на наклонную скамью.",
            "Выжмите гантели над грудью ладонями вперёд.",
            "Плавно опустите их по сторонам груди и повторите.",
        ),
    ),
    R.drawable.ex_seated_cable_rows_0 to ExerciseTechnique(
        listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
        listOf(
            "Сядьте, возьмите V-рукоять и упритесь в платформу, слегка согнув колени.",
            "Выпрямите корпус и подтяните рукоять к животу, ведя локти вдоль боков.",
            "Сведите лопатки и ненадолго замрите.",
            "Медленно выпрямите руки, сохраняя положение корпуса.",
        ),
    ),
    R.drawable.ex_onearm_dumbbell_row_0 to ExerciseTechnique(
        listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
        listOf(
            "Обопритесь ладонью и коленом на скамью, держите спину ровно.",
            "Свободной рукой потяните гантель к боку, не разворачивая корпус.",
            "Опустите гантель под контролем. Закончив повторения, смените руку.",
        ),
    ),
    R.drawable.ex_pullups_0 to ExerciseTechnique(
        listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack),
        listOf(
            "Повисните на перекладине хватом сверху, выпрямив руки.",
            "Ведите локти вниз и подтяните верх груди к перекладине.",
            "Коротко задержитесь и плавно опуститесь в вис.",
        ),
    ),
    R.drawable.ex_barbell_deadlift_0 to ExerciseTechnique(
        listOf(Muscle.LowerBack), listOf(Muscle.Calves, Muscle.Forearms, Muscle.Glutes, Muscle.Hamstrings, Muscle.Lats, Muscle.MiddleBack, Muscle.Quadriceps, Muscle.Traps),
        listOf(
            "Присядьте к штанге, возьмитесь за гриф и выпрямите спину.",
            "Встаньте, одновременно разгибая колени и тазобедренные суставы.",
            "Отведите таз назад, согните колени и опустите штангу на пол.",
        ),
    ),
    R.drawable.ex_barbell_squat_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.LowerBack),
        listOf(
            "Снимите штангу со стоек, расположив гриф на верхней части спины.",
            "На вдохе согните колени и опустите таз, удерживая корпус собранным.",
            "Оттолкнитесь всей стопой от пола и встаньте на выдохе.",
            "После подхода верните штангу на стойки.",
        ),
    ),
    R.drawable.ex_leg_press_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Сядьте, поставьте стопы на платформу на ширине плеч и снимите фиксаторы.",
            "Согните колени и медленно опустите платформу, сохраняя опору спиной.",
            "Выжмите платформу, оставляя колени слегка согнутыми. После подхода закройте фиксаторы.",
        ),
    ),
    R.drawable.ex_leg_extensions_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), emptyList(),
        listOf(
            "Настройте валик над стопами и сядьте, прижав спину к опоре.",
            "Разогните колени и задержитесь вверху.",
            "Плавно опустите валик, сохраняя положение корпуса.",
        ),
    ),
    R.drawable.ex_lying_leg_curls_0 to ExerciseTechnique(
        listOf(Muscle.Hamstrings), emptyList(),
        listOf(
            "Лягте на живот, расположив валик у задней стороны голеней над пятками.",
            "Согните колени, не отрывая бёдра от скамьи.",
            "Медленно разогните ноги.",
        ),
    ),
    R.drawable.ex_dumbbell_shoulder_press_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
        listOf(
            "Сядьте со спинкой и поднимите гантели к плечам, ладонями вперёд.",
            "Выжмите гантели вверх и коротко задержитесь.",
            "Плавно верните их на уровень плеч.",
        ),
    ),
    R.drawable.ex_side_lateral_raise_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), emptyList(),
        listOf(
            "Встаньте, опустив гантели по бокам ладонями к корпусу.",
            "Слегка согните локти и поднимите руки в стороны до уровня плеч.",
            "Медленно опустите гантели, удерживая корпус неподвижно.",
        ),
    ),
    R.drawable.ex_hammer_curls_0 to ExerciseTechnique(
        listOf(Muscle.Biceps), emptyList(),
        listOf(
            "Встаньте с гантелями, развернув ладони к бёдрам.",
            "Прижмите локти к бокам и согните руки, сохраняя нейтральный хват.",
            "Сделайте короткую паузу и плавно опустите гантели.",
        ),
    ),
    R.drawable.ex_triceps_pushdown_rope_attachment_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), emptyList(),
        listOf(
            "Возьмите концы каната верхнего блока и прижмите локти к бокам.",
            "Разогните руки вниз, разводя концы каната возле бёдер.",
            "Плавно согните локти, удерживая плечи на месте.",
        ),
    ),
    R.drawable.ex_cable_crossover_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders),
        listOf(
            "Возьмите рукояти верхних блоков, шагните вперёд и слегка наклонитесь.",
            "Сведите руки перед грудью по дуге, сохраняя небольшой сгиб в локтях.",
            "Плавно разведите руки, чтобы почувствовать растяжение груди.",
        ),
    ),
    R.drawable.ex_barbell_incline_bench_press_medium_grip_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Лягте на наклонную скамью и снимите штангу хватом средней ширины.",
            "Плавно опустите гриф к верхней части груди.",
            "После короткой паузы выжмите штангу вверх.",
            "Завершив подход, верните штангу на стойки.",
        ),
    ),
    R.drawable.ex_dumbbell_flyes_0 to ExerciseTechnique(
        listOf(Muscle.Chest), emptyList(),
        listOf(
            "Лягте на скамью и поднимите гантели над грудью ладонями друг к другу.",
            "Слегка согните локти и разведите руки по дуге до растяжения груди.",
            "Сведите гантели по той же дуге, сохраняя угол в локтях.",
        ),
    ),
    R.drawable.ex_dips_chest_version_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Примите упор на брусьях и наклоните корпус вперёд.",
            "Согните руки, позволяя локтям немного уйти в стороны.",
            "Когда почувствуете растяжение груди, выжмите себя обратно в упор.",
        ),
    ),
    R.drawable.ex_pushups_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Примите упор лёжа на ладонях и носках, вытянув тело в прямую линию.",
            "Согните локти и опустите грудь почти до пола.",
            "Оттолкнитесь ладонями и вернитесь в упор.",
        ),
    ),
    R.drawable.ex_leverage_chest_press_0 to ExerciseTechnique(
        listOf(Muscle.Chest), listOf(Muscle.Shoulders, Muscle.Triceps),
        listOf(
            "Настройте сиденье так, чтобы рукояти были на уровне середины или нижней части груди.",
            "Прижмите спину к спинке и выжмите рукояти вперёд.",
            "После короткой паузы плавно верните рукояти.",
        ),
    ),
    R.drawable.ex_bent_over_barbell_row_0 to ExerciseTechnique(
        listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats, Muscle.Shoulders),
        listOf(
            "Возьмите штангу хватом сверху, слегка согните колени и наклоните корпус с ровной спиной.",
            "Потяните гриф к корпусу, ведя локти назад и сводя лопатки.",
            "Медленно опустите штангу, сохраняя наклон.",
        ),
    ),
    R.drawable.ex_tbar_row_with_handle_0 to ExerciseTechnique(
        listOf(Muscle.MiddleBack), listOf(Muscle.Biceps, Muscle.Lats),
        listOf(
            "Встаньте над закреплённым грифом и возьмите V-рукоять у блинов.",
            "Поставьте ноги широко, отведите таз назад и выпрямите руки.",
            "Подтяните вес к верхней части живота, сведя лопатки.",
            "После паузы плавно опустите вес.",
        ),
    ),
    R.drawable.ex_closegrip_front_lat_pulldown_0 to ExerciseTechnique(
        listOf(Muscle.Lats), listOf(Muscle.Biceps, Muscle.MiddleBack, Muscle.Shoulders),
        listOf(
            "Сядьте под верхним блоком, зафиксируйте бёдра и возьмите гриф узким хватом сверху.",
            "Слегка отклоните корпус и потяните гриф к верхней части груди.",
            "Сведите лопатки, затем медленно выпрямите руки.",
        ),
    ),
    R.drawable.ex_hyperextensions_back_extensions_0 to ExerciseTechnique(
        listOf(Muscle.LowerBack), listOf(Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Упритесь бёдрами в подушку и закрепите стопы, оставив тазу свободу движения.",
            "Наклонитесь вперёд через тазобедренные суставы, сохраняя спину ровной.",
            "Поднимите корпус до прямой линии с ногами.",
        ),
    ),
    R.drawable.ex_straightarm_pulldown_0 to ExerciseTechnique(
        listOf(Muscle.Lats), emptyList(),
        listOf(
            "Возьмите гриф верхнего блока широким хватом сверху и отступите назад.",
            "Слегка наклоните корпус и оставьте локти чуть согнутыми.",
            "По дуге опустите гриф к бёдрам и плавно поднимите обратно, сохраняя угол в локтях.",
        ),
    ),
    R.drawable.ex_dumbbell_shrug_0 to ExerciseTechnique(
        listOf(Muscle.Traps), emptyList(),
        listOf(
            "Встаньте с гантелями в опущенных руках, ладони обращены к телу.",
            "Поднимите плечи к ушам, сохраняя руки прямыми.",
            "Задержитесь вверху и плавно опустите плечи.",
        ),
    ),
    R.drawable.ex_romanian_deadlift_0 to ExerciseTechnique(
        listOf(Muscle.Hamstrings), listOf(Muscle.Calves, Muscle.Glutes, Muscle.LowerBack),
        listOf(
            "Возьмите штангу хватом сверху и встаньте, чуть согнув колени.",
            "Отведите таз назад и опустите гриф вдоль ног до натяжения задней стороны бёдер.",
            "Выпрямитесь, подавая таз вперёд.",
        ),
    ),
    R.drawable.ex_dumbbell_lunges_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Встаньте прямо с гантелями по бокам и шагните вперёд.",
            "Согните оба колена, сохраняя корпус вертикальным.",
            "Оттолкнитесь передней стопой и вернитесь в стойку. Смените ногу.",
        ),
    ),
    R.drawable.ex_split_squat_with_dumbbells_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Поставьте заднюю стопу на скамью, переднюю вынесите вперёд и возьмите гантели.",
            "Согните переднюю ногу и опустите заднее колено к полу.",
            "Оттолкнитесь передней стопой и выпрямитесь. Затем смените ногу.",
        ),
    ),
    R.drawable.ex_standing_calf_raises_0 to ExerciseTechnique(
        listOf(Muscle.Calves), emptyList(),
        listOf(
            "Настройте упоры под плечи и поставьте носки на платформу, оставив пятки свободными.",
            "Поднимите пятки максимально высоко, удерживая колени почти прямыми.",
            "Задержитесь и плавно опустите пятки ниже платформы.",
        ),
    ),
    R.drawable.ex_seated_calf_raise_0 to ExerciseTechnique(
        listOf(Muscle.Calves), emptyList(),
        listOf(
            "Поставьте носки на платформу и настройте упор над коленями.",
            "Поднимите пятки и освободите фиксатор. Опустите пятки до растяжения икр.",
            "Поднимитесь на носки, задержитесь и плавно опуститесь.",
        ),
    ),
    R.drawable.ex_barbell_hip_thrust_0 to ExerciseTechnique(
        listOf(Muscle.Glutes), listOf(Muscle.Calves, Muscle.Hamstrings),
        listOf(
            "Сядьте перед скамьёй и расположите гриф на тазу, подложив мягкую накладку.",
            "Обопритесь верхом спины о скамью и прижмите стопы к полу.",
            "Поднимите таз, опираясь на пятки, и плавно опустите его обратно.",
        ),
    ),
    R.drawable.ex_goblet_squat_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.Shoulders),
        listOf(
            "Удерживайте гирю за дужку близко к груди.",
            "Присядьте между ног, сохраняя пятки на полу и грудь поднятой.",
            "Оттолкнитесь стопами и вернитесь в стойку.",
        ),
    ),
    R.drawable.ex_hack_squat_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Упритесь спиной и плечами в подушки, поставьте стопы на платформу и снимите фиксаторы.",
            "Медленно согните колени, не отрывая стопы от платформы.",
            "Разогните ноги, толкая платформу пятками, и после подхода верните фиксаторы.",
        ),
    ),
    R.drawable.ex_thigh_adductor_0 to ExerciseTechnique(
        listOf(Muscle.Adductors), listOf(Muscle.Glutes, Muscle.Hamstrings),
        listOf(
            "Сядьте и поместите внутреннюю поверхность бёдер у упоров, возьмитесь за ручки.",
            "Плавно сведите ноги, сохраняя корпус неподвижным.",
            "После короткой паузы медленно разведите ноги.",
        ),
    ),
    R.drawable.ex_thigh_abductor_0 to ExerciseTechnique(
        listOf(Muscle.Abductors), listOf(Muscle.Glutes),
        listOf(
            "Сядьте и прижмите наружную поверхность бёдер к упорам, возьмитесь за ручки.",
            "Разведите колени, удерживая корпус неподвижно.",
            "Выдержите паузу и плавно сведите ноги.",
        ),
    ),
    R.drawable.ex_smith_machine_squat_0 to ExerciseTechnique(
        listOf(Muscle.Quadriceps), listOf(Muscle.Calves, Muscle.Glutes, Muscle.Hamstrings, Muscle.LowerBack),
        listOf(
            "Поставьте гриф на верх спины, возьмитесь двумя руками и снимите его с фиксаторов.",
            "Поставьте стопы примерно на ширине плеч и плавно присядьте.",
            "Оттолкнитесь пятками и выпрямитесь. В конце подхода снова зафиксируйте гриф.",
        ),
    ),
    R.drawable.ex_standing_military_press_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), listOf(Muscle.Triceps),
        listOf(
            "Возьмите штангу со стоек хватом сверху чуть шире плеч и удерживайте её у ключиц.",
            "Выжмите гриф над головой, сохраняя корпус собранным.",
            "Плавно опустите штангу к ключицам.",
        ),
    ),
    R.drawable.ex_reverse_flyes_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), emptyList(),
        listOf(
            "Лягте грудью на наклонную скамью, опустив гантели ладонями друг к другу.",
            "Слегка согните локти и разведите руки, сводя лопатки.",
            "Плавно опустите гантели, сохраняя сгиб в локтях.",
        ),
    ),
    R.drawable.ex_front_dumbbell_raise_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), emptyList(),
        listOf(
            "Встаньте с гантелями перед бёдрами, ладони к себе.",
            "Поднимите одну гантель перед собой чуть выше уровня плеча, слегка согнув локоть.",
            "Плавно опустите руку и повторите другой рукой.",
        ),
    ),
    R.drawable.ex_upright_barbell_row_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), listOf(Muscle.Traps),
        listOf(
            "Возьмите штангу хватом сверху чуть уже плеч и опустите к бёдрам.",
            "Ведите локти вверх и в стороны, подтягивая гриф близко к телу.",
            "После короткой паузы плавно опустите гриф.",
        ),
    ),
    R.drawable.ex_face_pull_0 to ExerciseTechnique(
        listOf(Muscle.Shoulders), listOf(Muscle.MiddleBack),
        listOf(
            "Встаньте лицом к верхнему блоку и возьмите канат.",
            "Потяните канат к лицу, разводя кисти и удерживая плечи примерно параллельно полу.",
            "Медленно выпрямите руки.",
        ),
    ),
    R.drawable.ex_barbell_curl_0 to ExerciseTechnique(
        listOf(Muscle.Biceps), listOf(Muscle.Forearms),
        listOf(
            "Возьмите штангу хватом снизу примерно на ширине плеч.",
            "Прижав локти к бокам, поднимите гриф, сгибая руки.",
            "После короткой паузы плавно разогните руки.",
        ),
    ),
    R.drawable.ex_dumbbell_alternate_bicep_curl_0 to ExerciseTechnique(
        listOf(Muscle.Biceps), listOf(Muscle.Forearms),
        listOf(
            "Встаньте с гантелями по бокам и прижмите локти к корпусу.",
            "Согните одну руку, разворачивая ладонь вверх.",
            "Плавно опустите гантель, верните ладонь к бедру и повторите другой рукой.",
        ),
    ),
    R.drawable.ex_standing_biceps_cable_curl_0 to ExerciseTechnique(
        listOf(Muscle.Biceps), emptyList(),
        listOf(
            "Возьмите прямую рукоять нижнего блока хватом снизу на ширине плеч.",
            "Удерживая локти у корпуса, согните руки и поднимите рукоять к плечам.",
            "После паузы плавно опустите рукоять.",
        ),
    ),
    R.drawable.ex_preacher_curl_0 to ExerciseTechnique(
        listOf(Muscle.Biceps), emptyList(),
        listOf(
            "Возьмите EZ-гриф узким хватом и положите плечи на подушку скамьи.",
            "Медленно опустите гриф, почти полностью разогнув руки.",
            "Поднимите гриф, сгибая локти, и задержитесь вверху.",
        ),
    ),
    R.drawable.ex_triceps_pushdown_vbar_attachment_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), emptyList(),
        listOf(
            "Возьмите V-рукоять верхнего блока хватом сверху и прижмите локти к бокам.",
            "Разогните руки так, чтобы рукоять опустилась к передней стороне бёдер.",
            "Выдержите короткую паузу и плавно согните руки.",
        ),
    ),
    R.drawable.ex_cable_rope_overhead_triceps_extension_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), emptyList(),
        listOf(
            "Возьмите канат нижнего блока, повернитесь спиной к блоку и вытяните руки над головой.",
            "Согните локти, опуская канат за голову и удерживая плечи неподвижно.",
            "Разогните руки вверх и ненадолго задержитесь.",
        ),
    ),
    R.drawable.ex_ezbar_skullcrusher_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), listOf(Muscle.Forearms),
        listOf(
            "Лягте на скамью и поднимите EZ-гриф узким хватом над собой.",
            "Не двигая плечами, согните локти и опустите гриф к уровню лба.",
            "Плавно разогните руки и верните гриф наверх.",
        ),
    ),
    R.drawable.ex_closegrip_barbell_bench_press_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), listOf(Muscle.Chest, Muscle.Shoulders),
        listOf(
            "Лягте на скамью и снимите штангу хватом примерно на ширине плеч.",
            "Опустите гриф к середине груди, держа локти близко к корпусу.",
            "После паузы выжмите штангу вверх. В конце подхода верните её на стойки.",
        ),
    ),
    R.drawable.ex_dips_triceps_version_0 to ExerciseTechnique(
        listOf(Muscle.Triceps), listOf(Muscle.Chest, Muscle.Shoulders),
        listOf(
            "Примите упор на брусьях, удерживая корпус вертикально.",
            "Опускайтесь, ведя локти назад, пока они не согнутся примерно под прямым углом.",
            "Выпрямите руки и вернитесь в упор.",
        ),
    ),
    R.drawable.ex_crunches_0 to ExerciseTechnique(
        listOf(Muscle.Abdominals), emptyList(),
        listOf(
            "Лягте на спину, согните колени и положите руки рядом с висками.",
            "На выдохе приподнимите плечи, оставляя поясницу на полу.",
            "Коротко задержитесь и медленно опустите плечи.",
        ),
    ),
    R.drawable.ex_hanging_leg_raise_0 to ExerciseTechnique(
        listOf(Muscle.Abdominals), emptyList(),
        listOf(
            "Повисните на перекладине с выпрямленными руками и ногами.",
            "Подкрутите таз и поднимите ноги, не раскачиваясь.",
            "Ненадолго задержитесь и медленно опустите ноги.",
        ),
    ),
    R.drawable.ex_plank_0 to ExerciseTechnique(
        listOf(Muscle.Abdominals), emptyList(),
        listOf(
            "Обопритесь на носки и предплечья, расположив локти под плечами.",
            "Выстройте корпус и ноги в одну линию, напрягите живот.",
            "Удерживайте положение, не прогибаясь в пояснице.",
        ),
    ),
    R.drawable.ex_cable_crunch_0 to ExerciseTechnique(
        listOf(Muscle.Abdominals), emptyList(),
        listOf(
            "Встаньте на колени перед верхним блоком и подтяните концы каната к лицу.",
            "Слегка согните тазобедренные суставы и скрутите корпус, ведя локти к бёдрам.",
            "Плавно разогните корпус, сохраняя положение таза.",
        ),
    ),
)
