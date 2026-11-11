package fr.olivier.forme.feature.sharing

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A workout template shared between two phones (V2.27), described in docs/format-partage.md: its structure only,
 * never loads, history, weight, profile or health data; the author's first name only when chosen.
 */
@Serializable
data class SharedTemplateFile(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String,
    val author: String? = null,
    val template: SharedTemplate,
) {
    companion object {
        const val FORMAT = "gymflemme-template"
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class SharedTemplate(val name: String, val code: String? = null, val exercises: List<SharedExercise>)

/** [type] and [muscleGroup]: names of [ExerciseType] and [MuscleGroup]; [restSec] null = the default rest. */
@Serializable
data class SharedExercise(
    val position: Int,
    val libraryId: String? = null,
    val name: String,
    val type: String,
    val muscleGroup: String,
    val perSide: Boolean = false,
    val loadIncrementKg: Double = 2.5,
    val targetSets: Int,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val restSec: Int? = null,
    val optional: Boolean = false,
    /** Exercises sharing a number form a superset. */
    val supersetGroup: Int? = null,
) {
    val exerciseType: ExerciseType? get() = ExerciseType.entries.firstOrNull { it.name == type }
    val group: MuscleGroup? get() = MuscleGroup.entries.firstOrNull { it.name == muscleGroup }
}

sealed interface DecodeResult {
    data class Ok(val file: SharedTemplateFile) : DecodeResult

    /** Written by a newer GymFlemme: update the app to import it. */
    data class TooNew(val formatVersion: Int) : DecodeResult

    /** Not a GymFlemme template, or damaged. */
    data object Unreadable : DecodeResult
}

object TemplateShareCodec {
    const val QR_PREFIX = "GYMFLEMME1:"

    /** "Readable" on a phone screen: version 25 at most, error correction M (~1 000 bytes). */
    const val MAX_QR_VERSION = 25

    // Defaults written (format, formatVersion, false flags), nulls left out.
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val compact = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(file: SharedTemplateFile): String = json.encodeToString(SharedTemplateFile.serializer(), file)

    fun decode(text: String): DecodeResult {
        val file = runCatching { json.decodeFromString(SharedTemplateFile.serializer(), text) }.getOrNull()
            ?: return DecodeResult.Unreadable
        if (file.format != SharedTemplateFile.FORMAT) return DecodeResult.Unreadable
        if (file.formatVersion > SharedTemplateFile.FORMAT_VERSION) return DecodeResult.TooNew(file.formatVersion)
        val exercises = file.template.exercises
        val valid = file.template.name.isNotBlank() && exercises.isNotEmpty() &&
            exercises.all { it.name.isNotBlank() && it.exerciseType != null && it.group != null && it.targetSets in 1..20 }
        return if (valid) DecodeResult.Ok(file) else DecodeResult.Unreadable
    }

    /** "GYMFLEMME1:" + the compact JSON, compressed (deflate) and encoded in base64. */
    fun toQrPayload(file: SharedTemplateFile): String {
        val bytes = compact.encodeToString(SharedTemplateFile.serializer(), file).toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return QR_PREFIX + Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    fun isQrPayload(text: String) = text.startsWith(QR_PREFIX)

    fun fromQrPayload(text: String): DecodeResult {
        if (!isQrPayload(text)) return DecodeResult.Unreadable
        val bytes = runCatching { Base64.getDecoder().decode(text.removePrefix(QR_PREFIX).trim()) }.getOrNull()
            ?: return DecodeResult.Unreadable
        val inflater = Inflater(true).apply { setInput(bytes) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                if (out.size() > 200_000) return DecodeResult.Unreadable // not a template
            }
        } catch (e: DataFormatException) {
            return DecodeResult.Unreadable
        } finally {
            inflater.end()
        }
        return decode(out.toString(Charsets.UTF_8.name()))
    }

    /** The QR code version [payload] needs at level M; null when it does not fit at all. */
    fun qrVersion(payload: String): Int? = try {
        Encoder.encode(payload, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "ISO-8859-1")).version.versionNumber
    } catch (e: WriterException) {
        null
    }

    fun fitsInQr(payload: String): Boolean = qrVersion(payload)?.let { it <= MAX_QR_VERSION } == true
}
