package fr.olivier.forme.feature.heart.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HeartModule {
    @Binds
    abstract fun healthConnectSource(reader: HealthConnectReader): HealthConnectSource
}
