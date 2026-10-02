package fr.olivier.forme.feature.bodyweight.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyWeightRepository @Inject constructor(private val dao: BodyWeightDao) {
    fun observeAll(): Flow<List<BodyWeightEntity>> = dao.observeAll()
    suspend fun latest(): BodyWeightEntity? = dao.getLatest()
    suspend fun save(date: LocalDate, weightKg: Double) = dao.upsert(BodyWeightEntity(date = date, weightKg = weightKg))
    suspend fun delete(id: Long) = dao.delete(id)
}
