package fr.olivier.forme.feature.hydration.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.feature.hydration.health.HydrationHealthMirror
import fr.olivier.forme.feature.hydration.health.IntakeMirror
import fr.olivier.forme.feature.sport.data.SessionListener
import java.time.Instant

/** A session started gives the bonus of the day; abandoned without any set, it may take it back (V2.29). */
class HydrationSessionListener(private val hydration: HydrationRepository) : SessionListener {
    override suspend fun onSessionStarted(startedAt: Instant) = hydration.onSessionStarted(startedAt)
    override suspend fun onSessionAbandoned(startedAt: Instant, hadSets: Boolean) = hydration.onSessionAbandoned(startedAt, hadSets)
}

@Module
@InstallIn(SingletonComponent::class)
object HydrationModule {
    @Provides
    fun provideSessionListener(hydration: HydrationRepository): SessionListener = HydrationSessionListener(hydration)

    /** V2.29: intakes copied to Health Connect when the setting is on. */
    @Provides
    fun provideIntakeMirror(mirror: HydrationHealthMirror): IntakeMirror = mirror
}
