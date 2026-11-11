package fr.olivier.forme.feature.sharing

import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateLineWithExercise
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2.27: the shared template format, its QR code, the matching of the exercises and the name of the copy. */
class TemplateShareTest {
    private val pulldown = ExerciseEntity(
        id = 5, name = "Pull down", muscleGroup = MuscleGroup.DOS, type = ExerciseType.WEIGHTED, loadIncrementKg = 2.5,
        libraryId = "lat-pulldown", settingsNote = "Siège cran 4", loadNote = "+ poignée", photoUri = "/data/p.jpg", nextNote = "Monter",
    )
    private val plank = ExerciseEntity(id = 9, name = "Planche latérale", muscleGroup = MuscleGroup.ABDOS, type = ExerciseType.TIMED, perSide = true)
    private val template = WorkoutTemplateEntity(id = 2, name = "Dos", code = "B", orderInRotation = 2)
    private val lines = listOf(
        TemplateLineWithExercise(TemplateExerciseEntity(templateId = 2, exerciseId = 5, position = 0, targetSets = 3, targetRepsMin = 10, targetRepsMax = 12, restSec = 90, supersetGroup = 1), pulldown),
        TemplateLineWithExercise(TemplateExerciseEntity(templateId = 2, exerciseId = 9, position = 1, targetSets = 3, targetDurationSec = 25, optional = true, supersetGroup = 1), plank),
    )
    private val file = TemplateImport.export(template, lines, author = "Olivier", appVersion = "2.27.0")

    @Test
    fun theStructureOnlyIsShared() {
        val json = TemplateShareCodec.encode(file)
        assertTrue(json.contains("\"format\": \"gymflemme-template\""))
        assertTrue(json.contains("\"libraryId\": \"lat-pulldown\""))
        listOf("Siège", "poignée", "/data", "Monter", "orderInRotation", "weight").forEach { assertFalse(it, json.contains(it)) }
        assertNull(TemplateImport.export(template, lines, author = null, appVersion = "2.27.0").author)
    }

    @Test
    fun fileRoundTripIsLossless() {
        assertEquals(DecodeResult.Ok(file), TemplateShareCodec.decode(TemplateShareCodec.encode(file)))
        val e = file.template.exercises
        assertEquals(listOf(1, 2), e.map { it.position })
        assertEquals(1, e[1].supersetGroup)
        assertTrue(e[1].perSide && e[1].optional)
        assertEquals(25, e[1].targetDurationSec)
    }

    @Test
    fun qrRoundTripAndSize() {
        val payload = TemplateShareCodec.toQrPayload(file)
        assertTrue(payload.startsWith("GYMFLEMME1:"))
        assertEquals(DecodeResult.Ok(file), TemplateShareCodec.fromQrPayload(payload))
        assertTrue(TemplateShareCodec.fitsInQr(payload))
        // A template of 8 exercises, names as in the seed data: still a readable code.
        val eight = file.copy(template = file.template.copy(exercises = (1..8).map { i ->
            file.template.exercises[i % 2].copy(position = i, name = listOf("Rameur", "Shoulder press", "Chest press", "Pec machine", "Pull down prise neutre", "Rowing machine", "Élévations latérales", "Abdominal crunch machine")[i - 1], libraryId = "library-exercise-$i")
        }))
        val eightPayload = TemplateShareCodec.toQrPayload(eight)
        assertTrue(TemplateShareCodec.fitsInQr(eightPayload))
        // 40 exercises with long names that do not compress: too long for a readable code.
        val random = java.util.Random(7)
        fun noise(n: Int) = (1..n).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")
        val big = file.copy(
            template = file.template.copy(
                exercises = (1..40).map { i -> file.template.exercises[0].copy(position = i, name = noise(30), libraryId = noise(20)) },
            ),
        )
        val bigPayload = TemplateShareCodec.toQrPayload(big)
        assertFalse(TemplateShareCodec.fitsInQr(bigPayload))
        assertEquals(DecodeResult.Ok(big), TemplateShareCodec.fromQrPayload(bigPayload)) // still a valid payload
    }

    @Test
    fun aNewerFormatIsRefusedOthersAreUnreadable() {
        val newer = TemplateShareCodec.encode(file).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(DecodeResult.TooNew(2), TemplateShareCodec.decode(newer))
        assertEquals(DecodeResult.Unreadable, TemplateShareCodec.decode("{\"hello\": 1}"))
        assertEquals(DecodeResult.Unreadable, TemplateShareCodec.decode("not json"))
        assertEquals(DecodeResult.Unreadable, TemplateShareCodec.decode(TemplateShareCodec.encode(file).replace("\"WEIGHTED\"", "\"FLYING\"")))
        assertEquals(DecodeResult.Unreadable, TemplateShareCodec.fromQrPayload("GYMFLEMME1:!!!"))
        assertEquals(DecodeResult.Unreadable, TemplateShareCodec.fromQrPayload("https://example.com"))
    }

    @Test
    fun matchingByLibraryThenNameElseNew() {
        val mine = listOf(
            ExerciseEntity(id = 40, name = "Tirage vertical", muscleGroup = MuscleGroup.DOS, type = ExerciseType.WEIGHTED, libraryId = "lat-pulldown"),
            ExerciseEntity(id = 41, name = "PLANCHE LATERALE", muscleGroup = MuscleGroup.ABDOS, type = ExerciseType.TIMED),
            ExerciseEntity(id = 42, name = "Lower back", muscleGroup = MuscleGroup.DOS, type = ExerciseType.WEIGHTED, archived = true),
        )
        val lowerBack = file.template.exercises[0].copy(position = 3, name = "Lower back", libraryId = null)
        val m = TemplateImport.match(file.template.exercises + lowerBack, mine)
        assertEquals(listOf(MatchKind.LIBRARY, MatchKind.NAME, MatchKind.NEW), m.map { it.kind })
        assertEquals(listOf(40L, 41L, null), m.map { it.exercise?.id })
        // A new exercise takes the file's values.
        val created = TemplateImport.newExercise(lowerBack)
        assertEquals(0L, created.id)
        assertEquals(MuscleGroup.DOS, created.muscleGroup)
        assertEquals(10, created.defaultRepsMin)
    }

    @Test
    fun nameConflict() {
        assertEquals("Dos", TemplateImport.importName("Dos", "Léa", listOf("Jambes")))
        assertEquals("Dos (Léa)", TemplateImport.importName("Dos", "Léa", listOf("dos")))
        assertEquals("Dos (importé)", TemplateImport.importName("Dos", null, listOf("Dos")))
        assertEquals("Dos (importé) 2", TemplateImport.importName("Dos", " ", listOf("Dos", "Dos (importé)")))
    }

    @Test
    fun fileName() {
        assertEquals("b-dos.gymflemme", TemplateImport.fileName("B · Dos"))
        assertEquals("poussee-tronc.gymflemme", TemplateImport.fileName("Poussée + tronc"))
    }

    @Test
    fun theDrawnQrCodeReadsBack() {
        val payload = TemplateShareCodec.toQrPayload(file)
        val hints = mapOf(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
            com.google.zxing.EncodeHintType.MARGIN to 2,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
        val pixels = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val bitmap = com.google.zxing.BinaryBitmap(
            com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(m.width, m.height, pixels)),
        )
        val read = com.google.zxing.qrcode.QRCodeReader().decode(bitmap, mapOf(com.google.zxing.DecodeHintType.PURE_BARCODE to true)).text
        assertEquals(payload, read)
        assertEquals(DecodeResult.Ok(file), TemplateShareCodec.fromQrPayload(read))
    }
}
