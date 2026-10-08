package fr.olivier.forme.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one automatic backup run. [targetError] is null when the target was written or none is set. */
data class AutoBackupResult(val localCopy: File, val targetWritten: Boolean, val targetError: String?)

/**
 * Automatic backup, without any Google account in the app: the user picks the target file once with the system
 * picker (Storage Access Framework, typically a folder in Google Drive) and the app keeps a persistable write grant
 * on that single document. Each run rewrites it in place; the Drive app uploads it (later if offline) and keeps the
 * previous versions. The last [LOCAL_KEEP] copies are also kept in the app's private storage, in case the target
 * becomes unreachable.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Stores [uri] as the new target (releasing the previous grant), then writes a first backup to it. */
    suspend fun setTarget(uri: Uri): AutoBackupResult {
        val previous = settings.autoBackup.first().targetUri
        if (previous != null && previous != uri.toString()) releaseGrant(Uri.parse(previous))
        runCatching { context.contentResolver.takePersistableUriPermission(uri, GRANT_FLAGS) }
            .onFailure { Log.w(TAG, "No persistable grant for $uri", it) }
        settings.setAutoBackupTarget(uri.toString())
        return runNow()
    }

    suspend fun clearTarget() {
        settings.autoBackup.first().targetUri?.let { releaseGrant(Uri.parse(it)) }
        settings.setAutoBackupTarget(null)
    }

    /**
     * Writes the local copy, then the target if one is set; each one is read back and checked (V2.6, see
     * [BackupVerifier]). The target outcome is recorded in the settings.
     */
    suspend fun runNow(): AutoBackupResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val bytes = BackupCodec.encode(backup.snapshot()).toByteArray()
            val local = writeLocalCopy(bytes)
            val localError = BackupVerifier.check(bytes, runCatching { local.readBytes() }.getOrNull())
                ?.let { "Copie locale non vérifiée : $it" }
            val target = settings.autoBackup.first().targetUri?.toUri()
                ?: return@withContext AutoBackupResult(local, targetWritten = false, targetError = localError)
            val error = runCatching { writeTarget(target, bytes) }
                .onFailure { Log.w(TAG, "Auto backup to target failed", it) }
                .exceptionOrNull()
                ?.let(::describe)
                ?: BackupVerifier.check(bytes, runCatching { readTarget(target) }.getOrNull())
                    ?.let { "Sauvegarde écrite mais non vérifiée : $it" }
                ?: localError
            settings.recordAutoBackup(clock.millis(), error)
            AutoBackupResult(local, targetWritten = error == null, targetError = error)
        }
    }

    /** Human-readable name of the target file (e.g. "gymflemme-sauvegarde.json"), when the provider tells it. */
    suspend fun targetName(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun localCopies(): List<File> =
        localDir().listFiles { f -> f.name.startsWith(LOCAL_PREFIX) || OLD_LOCAL_PREFIXES.any { f.name.startsWith(it) } }
            ?.sortedByDescending { it.name.substringAfter("-auto-") }
            .orEmpty()

    private fun writeTarget(uri: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        // "wt" (truncate) is the right mode; a few providers only accept "w", which they treat as a full rewrite.
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException(uri.toString())
        stream.use { it.write(bytes) }
    }

    private fun readTarget(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun writeLocalCopy(bytes: ByteArray): File {
        val dir = localDir()
        val stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "$LOCAL_PREFIX$stamp.json")
        file.writeBytes(bytes)
        localCopies().drop(LOCAL_KEEP).forEach { it.delete() }
        return file
    }

    private fun localDir() = File(context.filesDir, "auto-backups").apply { mkdirs() }

    private fun releaseGrant(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, GRANT_FLAGS) }
    }

    private fun describe(error: Throwable): String = when (error) {
        is SecurityException -> "Accès au fichier perdu : choisis à nouveau l'emplacement."
        is FileNotFoundException -> "Fichier introuvable (supprimé ou déplacé ?) : choisis à nouveau l'emplacement."
        else -> error.message ?: error.javaClass.simpleName
    }

    companion object {
        const val LOCAL_KEEP = 3
        const val TARGET_FILE_NAME = "gymflemme-sauvegarde.json"
        private const val LOCAL_PREFIX = "gymflemme-auto-"

        /** Local copies made under the app's former names (Forme, then GymBro): kept, then pruned with the new ones. */
        private val OLD_LOCAL_PREFIXES = listOf("forme-auto-", "gymbro-auto-")
        private const val TAG = "Forme"
        private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
