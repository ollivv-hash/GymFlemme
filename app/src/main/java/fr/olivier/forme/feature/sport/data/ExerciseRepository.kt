package fr.olivier.forme.feature.sport.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<ExerciseEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<ExerciseEntity>> = dao.observeActive()
    fun observe(id: Long): Flow<ExerciseEntity?> = dao.observeById(id)
    suspend fun get(id: Long): ExerciseEntity? = dao.getById(id)

    suspend fun save(exercise: ExerciseEntity): Long =
        if (exercise.id == 0L) dao.insert(exercise) else exercise.id.also { dao.update(exercise) }

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    /** Null or blank clears the note. */
    suspend fun setNextNote(id: Long, note: String?) = dao.setNextNote(id, note?.trim()?.ifEmpty { null })

    /**
     * Copies a picked photo into private storage (the picker grant is temporary), downscaled
     * to keep the file small. Returns the absolute path to store in [ExerciseEntity.photoUri].
     */
    suspend fun importPhoto(source: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_PX) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        file.absolutePath
    }

    fun deletePhoto(path: String?) {
        if (path != null) File(path).takeIf { it.parentFile?.name == "photos" }?.delete()
    }

    private companion object {
        const val PHOTO_MAX_PX = 1280
    }
}
