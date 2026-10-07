package fr.olivier.forme.feature.library.data

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(private val dao: LibraryDao) {
    fun observeAll(): Flow<List<LibraryExerciseEntity>> = dao.observeAll()
    suspend fun get(id: String): LibraryExerciseEntity? = dao.get(id)
    fun observe(id: String): Flow<LibraryExerciseEntity?> = dao.observe(id)

    companion object {
        /** Name or alias contains every word of [query], ignoring case and accents. */
        fun search(entries: List<LibraryExerciseEntity>, query: String): List<LibraryExerciseEntity> {
            val words = normalize(query).split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return entries
            return entries.filter { e ->
                val text = normalize(e.name + " " + e.aliasList.joinToString(" "))
                words.all { it in text }
            }
        }

        private fun normalize(s: String) =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('-', ' ')
    }
}
