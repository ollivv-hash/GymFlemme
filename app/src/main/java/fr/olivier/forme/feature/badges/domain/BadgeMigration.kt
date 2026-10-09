package fr.olivier.forme.feature.badges.domain

/**
 * The V2.17 badges in the V2.18 model (database migration 7 → 8 and backups of format 7): a badge already earned is
 * kept, with its day, as the matching tier. One-off badges keep their id, without tier.
 */
object BadgeMigration {
    /** V2.17 id → V2.18 badge id and tier number. */
    val V1_TO_V2: Map<String, Pair<String, Int>> = mapOf(
        "REPEAT_OFFENDER" to ("REPEAT_OFFENDER" to Tier.BRONZE.number),
        "PILLAR" to ("REPEAT_OFFENDER" to Tier.SILVER.number),
        "BEATS_THE_COUCH" to ("BEATS_THE_COUCH" to Tier.BRONZE.number),
        "ALL_ROUNDER" to ("ALL_ROUNDER" to Tier.BRONZE.number),
        "CREATINE_LOYAL" to ("CREATINE_LOYAL" to Tier.SILVER.number),
    )

    fun fromV1(id: String): Pair<String, Int> = V1_TO_V2[id] ?: (id to 0)

    /** The same conversion as SQL CASE expressions on the column `id`: (new id, tier). */
    val sqlId: String = "CASE `id` " + V1_TO_V2.entries.joinToString(" ") { (old, new) -> "WHEN '$old' THEN '${new.first}'" } + " ELSE `id` END"
    val sqlTier: String = "CASE `id` " + V1_TO_V2.entries.joinToString(" ") { (old, new) -> "WHEN '$old' THEN ${new.second}" } + " ELSE 0 END"
}
