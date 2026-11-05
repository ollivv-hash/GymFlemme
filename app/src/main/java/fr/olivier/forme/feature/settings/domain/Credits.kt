package fr.olivier.forme.feature.settings.domain

/** A data source or software component shipped in the app, with its licence (Config → Sources et licences). */
data class Credit(
    val title: String,
    val author: String,
    val usage: String,
    val license: String,
    val licenseUrl: String,
    val url: String,
    val note: String? = null,
)

object Credits {
    const val LIBRARY_COMMIT = "2786aefa4b36a08faeca8114e0d1eb0af3ef6732"

    val data = listOf(
        Credit(
            title = "Open Exercise Illustrations",
            author = "hitmanrepo",
            usage = "Illustrations et fiches de la bibliothèque d'exercices (415 exercices, 44 variantes), données 1.0.0, " +
                "commit ${LIBRARY_COMMIT.take(7)} ; images réencodées (WebP qualité 80, mêmes dimensions) pour alléger l'app.",
            license = "CC0 1.0 Universel (domaine public)",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/deed.fr",
            url = "https://github.com/hitmanrepo/open-exercise-illustrations/tree/$LIBRARY_COMMIT",
            note = "Illustrations générées par IA et vérifiées à l'œil, sans validation par un coach ni un professionnel de " +
                "santé : à titre indicatif, ce ne sont ni des conseils d'entraînement ni des conseils médicaux. Aucune " +
                "attribution n'est exigée ; elle est faite ici par courtoisie.",
        ),
        Credit(
            title = "Table Ciqual 2025",
            author = "Anses",
            usage = "Composition nutritionnelle des aliments (3 484 aliments), embarquée.",
            license = "Licence Ouverte / Etalab 2.0",
            licenseUrl = "https://www.etalab.gouv.fr/licence-ouverte-open-licence/",
            url = "https://ciqual.anses.fr/",
        ),
        Credit(
            title = "Open Food Facts",
            author = "Contributeurs d'Open Food Facts",
            usage = "Produits trouvés par leur code-barres (interrogé en ligne, puis gardés en cache).",
            license = "Open Database License (ODbL) 1.0",
            licenseUrl = "https://opendatacommons.org/licenses/odbl/1-0/",
            url = "https://world.openfoodfacts.org/",
        ),
    )

    val software = listOf(
        Credit(
            title = "Android Jetpack",
            author = "Google / Android Open Source Project",
            usage = "Compose, Material 3, Navigation, Room, DataStore, WorkManager, Glance, CameraX, Health Connect, " +
                "Lifecycle, Hilt.",
            license = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            url = "https://developer.android.com/jetpack",
        ),
        Credit(
            title = "Kotlin, coroutines et kotlinx.serialization",
            author = "JetBrains",
            usage = "Langage et bibliothèques de base.",
            license = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            url = "https://kotlinlang.org/",
        ),
        Credit(
            title = "ML Kit – lecture de codes-barres",
            author = "Google",
            usage = "Lecture des codes-barres, sur le téléphone, sans connexion.",
            license = "Conditions d'utilisation de ML Kit",
            licenseUrl = "https://developers.google.com/ml-kit/terms",
            url = "https://developers.google.com/ml-kit/vision/barcode-scanning",
        ),
        Credit(
            title = "Space Grotesk",
            author = "Florian Karsten et les auteurs du projet Space Grotesk",
            usage = "Police de l'écran d'accueil (V2.22), embarquée sans modification.",
            license = "SIL Open Font License 1.1",
            licenseUrl = "https://openfontlicense.org/open-font-license-official-text/",
            url = "https://github.com/floriankarsten/space-grotesk",
        ),
        Credit(
            title = "Reorderable",
            author = "Calvin Liang",
            usage = "Réorganisation des listes par glisser-déposer.",
            license = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            url = "https://github.com/Calvin-LL/Reorderable",
        ),
    )
}
