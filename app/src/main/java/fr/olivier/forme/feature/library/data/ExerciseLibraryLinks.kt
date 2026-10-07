package fr.olivier.forme.feature.library.data

/**
 * Library illustration of the user's 23 exercises (V2.8), by exact exercise name. Used by migration 5 → 6 to link
 * existing exercises and by the seed for new installs: frozen, a later change needs a new migration.
 * Lateral raises and biceps curl are done with dumbbells, the rest on machines. "Lower back" has no seated machine
 * in the library: linked to the back extension bench, changeable from the exercise screen.
 */
object ExerciseLibraryLinks {
    val BY_NAME: Map<String, String> = linkedMapOf(
        "Rameur" to "rowing-machine",
        "Chest press" to "chest-press-machine",
        "Pec machine" to "pec-deck",
        "Chest incline" to "plate-loaded-incline-chest-press",
        "Shoulder press" to "shoulder-press-machine",
        "Élévations latérales" to "lateral-raises",
        "Reverse fly" to "reverse-pec-deck",
        "Pull down" to "lat-pulldown",
        "Pull down prise neutre" to "close-grip-lat-pulldown",
        "Rowing machine" to "machine-row",
        "Lower back" to "back-extensions",
        "Curl biceps" to "biceps-curl",
        "Arm extension" to "machine-triceps-extension",
        "Hack squat" to "hack-squat",
        "Leg press" to "seated-leg-press",
        "Leg curl" to "seated-leg-curl",
        "Leg extension" to "leg-extension",
        "Abducteurs machine" to "hip-abduction-machine",
        "Mollets debout" to "standing-calf-raise-machine",
        "Abdominal crunch machine" to "machine-abdominal-crunch",
        "Chaise romaine" to "captains-chair-knee-raise",
        "Planche latérale" to "side-plank",
        "Planche" to "plank",
    )
}
