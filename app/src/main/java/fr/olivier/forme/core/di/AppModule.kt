package fr.olivier.forme.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.db.Migrations
import fr.olivier.forme.core.db.SeedCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, clock: Clock): FormeDatabase =
        Room.databaseBuilder(context, FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()

    @Provides fun provideExerciseDao(db: FormeDatabase) = db.exerciseDao()
    @Provides fun provideTemplateDao(db: FormeDatabase) = db.templateDao()
    @Provides fun provideSessionDao(db: FormeDatabase) = db.sessionDao()
    @Provides fun provideBodyWeightDao(db: FormeDatabase) = db.bodyWeightDao()
    @Provides fun provideProfileDao(db: FormeDatabase) = db.profileDao()
    @Provides fun provideBadgeDao(db: FormeDatabase) = db.badgeDao()
    @Provides fun provideCiqualDao(db: FormeDatabase) = db.ciqualDao()
    @Provides fun provideFoodDao(db: FormeDatabase) = db.foodDao()
    @Provides fun provideMealDao(db: FormeDatabase) = db.mealDao()
    @Provides fun provideSupplementDao(db: FormeDatabase) = db.supplementDao()
    @Provides fun provideSessionHeartDao(db: FormeDatabase) = db.sessionHeartDao()
    @Provides fun provideLibraryDao(db: FormeDatabase) = db.libraryDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
