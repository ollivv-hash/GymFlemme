package fr.olivier.forme.feature.library.domain

import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup

/**
 * French labels of the library's list values (validated in V2.8 step 1). Exercise names stay in English, like the
 * user's catalogue. An unknown key (newer library) is shown as is.
 */
object LibraryLabels {
    val muscles = mapOf(
        "chest" to "Pectoraux", "shoulders" to "Épaules", "triceps" to "Triceps", "biceps" to "Biceps",
        "forearms" to "Avant-bras", "lats" to "Grand dorsal", "back" to "Haut et milieu du dos", "traps" to "Trapèzes",
        "lower_back" to "Lombaires", "core" to "Abdominaux", "obliques" to "Obliques", "glutes" to "Fessiers",
        "quads" to "Quadriceps", "hamstrings" to "Ischio-jambiers", "calves" to "Mollets", "tibialis" to "Tibial antérieur",
        "neck" to "Cou", "adductors" to "Adducteurs",
    )
    val levels = mapOf("beginner" to "Débutant", "intermediate" to "Intermédiaire", "advanced" to "Avancé")
    val categories = mapOf(
        "compound" to "Polyarticulaire", "isolation" to "Isolation", "conditioning" to "Cardio / conditionnement",
        "mobility" to "Mobilité", "stretch" to "Étirement",
    )
    val places = mapOf("gym" to "Salle", "machines" to "Machines", "home" to "Maison", "bodyweight" to "Poids du corps")
    val impacts = mapOf("none" to "Sans impact", "low" to "Impact faible", "high" to "Impact fort (sauts)")
    val views = mapOf(
        "front view" to "Vue de face", "side view" to "Vue de profil",
        "three-quarter front view" to "Vue de trois-quarts avant", "three-quarter rear view" to "Vue de trois-quarts arrière",
    )
    val patterns = mapOf(
        "push-h" to "Poussée horizontale", "push-v" to "Poussée verticale", "pull-h" to "Tirage horizontal",
        "pull-v" to "Tirage vertical", "squat" to "Squat", "hinge" to "Charnière de hanche", "lunge" to "Fente",
        "carry" to "Porté", "rotation" to "Rotation", "core" to "Gainage / abdos", "isometric" to "Isométrique",
        "plyo" to "Pliométrie", "mobility" to "Mobilité", "stretch" to "Étirement", "cardio" to "Cardio",
    )
    val equipment = mapOf(
        "ab-wheel" to "Roue abdominale", "anchor" to "Cale-pieds", "backpack" to "Sac à dos", "band" to "Élastique",
        "barbell" to "Barre", "battle-ropes" to "Cordes ondulatoires", "bench" to "Banc", "cable" to "Poulie",
        "captains-chair" to "Chaise romaine (dips)", "cardio-machine" to "Machine cardio", "decline-bench" to "Banc décliné",
        "doorway" to "Encadrement de porte", "dumbbell" to "Haltères", "ez-bar" to "Barre EZ", "foam-roller" to "Rouleau de massage",
        "glute-ham-developer" to "GHD", "incline-bench" to "Banc incliné", "jump-rope" to "Corde à sauter",
        "kettlebell" to "Kettlebell", "landmine" to "Landmine", "machine" to "Machine", "medicine-ball" to "Médecine-ball",
        "mini-band" to "Mini-bande", "parallel-bars" to "Barres parallèles", "parallettes" to "Parallettes", "plate" to "Disque",
        "plyo-box" to "Box de pliométrie", "post" to "Poteau solide", "preacher-bench" to "Pupitre à biceps",
        "pull-up-bar" to "Barre de traction", "roman-chair" to "Banc à lombaires", "smith-machine" to "Smith machine",
        "squat-rack" to "Rack à squat", "stability-ball" to "Swiss ball", "step" to "Step",
        "suspension-trainer" to "Sangles de suspension (TRX)", "table" to "Table solide", "towel" to "Serviette",
        "trap-bar" to "Trap bar", "wall" to "Mur", "wrist-roller" to "Rouleau d'avant-bras",
    )
    val machineTypes = mapOf(
        "ab-crunch" to "Machine à crunch", "air-bike" to "Air bike", "assisted-dip" to "Dips assistés",
        "assisted-pull-up" to "Tractions assistées", "belt-squat" to "Belt squat", "biceps-curl" to "Machine curl biceps",
        "chest-press" to "Chest press", "elliptical" to "Vélo elliptique", "glute-kickback" to "Kickback fessiers",
        "hack-squat" to "Hack squat", "high-row" to "Tirage haut (high row)", "hip-abduction" to "Machine abducteurs",
        "hip-adduction" to "Machine adducteurs", "hip-thrust" to "Machine hip thrust", "incline-press" to "Presse inclinée à disques",
        "lateral-raise" to "Machine élévations latérales", "leg-extension" to "Leg extension", "leg-press" to "Presse à cuisses inclinée",
        "lying-leg-curl" to "Leg curl allongé", "pec-deck" to "Pec deck (butterfly)", "pendulum-squat" to "Pendulum squat",
        "pullover" to "Machine pull-over", "rowing-machine" to "Rameur", "seated-calf" to "Mollets assis",
        "seated-dip" to "Dips assis", "seated-leg-curl" to "Leg curl assis", "seated-leg-press" to "Presse à cuisses assise",
        "seated-row" to "Rowing assis", "shoulder-press" to "Shoulder press", "ski-machine" to "Ski-erg",
        "stair-climber" to "Simulateur d'escaliers", "standing-calf" to "Mollets debout", "standing-leg-curl" to "Leg curl debout",
        "stationary-bike" to "Vélo d'appartement", "t-bar-row" to "Rowing T-bar", "tibialis" to "Machine tibial",
        "treadmill" to "Tapis de course", "triceps-extension" to "Machine extension triceps", "twister" to "Twister debout",
        "v-squat" to "V-squat",
    )

    fun muscle(key: String) = muscles[key] ?: key

    /** "Machine · Leg curl assis", "Haltères", "Poids du corps" (no equipment). */
    fun equipmentOf(entry: LibraryExerciseEntity): String {
        val items = entry.equipmentList.map { equipment[it] ?: it }.ifEmpty { listOf("Poids du corps") }
        val machine = entry.machineType?.let { machineTypes[it] ?: it }
        return (items + listOfNotNull(machine)).distinct().joinToString(" · ")
    }

    /** The user's 8 groups (weekly volume), from the first main muscle; conditioning / cardio moves go to Cardio. */
    fun muscleGroupOf(entry: LibraryExerciseEntity): MuscleGroup = when {
        entry.category == "conditioning" || entry.movementPattern == "cardio" -> MuscleGroup.CARDIO
        else -> when (entry.primaryList.firstOrNull()) {
            "chest" -> MuscleGroup.PECTORAUX
            "shoulders" -> MuscleGroup.EPAULES
            "lats", "back", "traps", "lower_back" -> MuscleGroup.DOS
            "biceps", "triceps", "forearms" -> MuscleGroup.BRAS
            "core", "obliques" -> MuscleGroup.ABDOS
            "quads", "hamstrings", "glutes", "calves", "adductors", "tibialis" -> MuscleGroup.JAMBES
            else -> MuscleGroup.AUTRE
        }
    }

    /** Type of an exercise added from the library: cardio machine, hold, bodyweight, otherwise load + reps. */
    fun exerciseTypeOf(entry: LibraryExerciseEntity): ExerciseType = when {
        "cardio-machine" in entry.equipmentList || entry.category == "conditioning" && entry.movementPattern == "cardio" -> ExerciseType.CARDIO
        entry.frameList == listOf("hold") -> ExerciseType.TIMED
        entry.equipmentList.isEmpty() -> ExerciseType.BODYWEIGHT
        else -> ExerciseType.WEIGHTED
    }
}
