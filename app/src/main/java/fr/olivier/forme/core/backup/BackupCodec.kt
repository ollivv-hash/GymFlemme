package fr.olivier.forme.core.backup

import kotlinx.serialization.json.Json

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** JSON encoding of [BackupFile]; pure, unit-tested for lossless round trips. */
object BackupCodec {
    const val CURRENT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true // forward compatibility: sections added later are skipped
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("Fichier de sauvegarde illisible : ${e.message}", e)
        }
        if (file.schemaVersion > CURRENT_VERSION) {
            throw BackupFormatException(
                "Sauvegarde au format v${file.schemaVersion}, plus récent que l'application (v$CURRENT_VERSION).",
            )
        }
        return file
    }
}
