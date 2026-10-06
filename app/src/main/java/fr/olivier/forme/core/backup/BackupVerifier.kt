package fr.olivier.forme.core.backup

/**
 * Restore test of a written backup, without touching the database: the bytes read back must be the ones written,
 * and they must decode as a backup this version can restore.
 */
object BackupVerifier {
    /** Null when [readBack] is a faithful, restorable copy of [written]; otherwise the reason, in French. */
    fun check(written: ByteArray, readBack: ByteArray?): String? = when {
        readBack == null || readBack.isEmpty() -> "fichier relu vide ou inaccessible"
        !readBack.contentEquals(written) -> "contenu relu différent de ce qui a été écrit (${readBack.size} / ${written.size} octets)"
        else -> runCatching { BackupCodec.decode(readBack.decodeToString()) }.exceptionOrNull()?.let { it.message ?: "illisible" }
    }
}
