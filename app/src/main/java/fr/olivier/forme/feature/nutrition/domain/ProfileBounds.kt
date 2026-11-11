package fr.olivier.forme.feature.nutrition.domain

/** Reasonable values for the profile (V2.25), shared by the first-launch wizard and Réglages → Profil. */
object ProfileBounds {
    val HEIGHT_CM = 100..250
    val WEIGHT_KG = 30.0..300.0
    val AGE = 13..100

    fun birthYears(currentYear: Int): IntRange = currentYear - AGE.last..currentYear - AGE.first

    fun height(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in HEIGHT_CM }
    fun birthYear(text: String, currentYear: Int): Int? = text.trim().toIntOrNull()?.takeIf { it in birthYears(currentYear) }
    fun weight(value: Double?): Double? = value?.takeIf { it in WEIGHT_KG }

    /** Calorie and protein targets a person may enter. */
    val KCAL = 1000..6000
    val PROTEIN_G = 30..400
}
