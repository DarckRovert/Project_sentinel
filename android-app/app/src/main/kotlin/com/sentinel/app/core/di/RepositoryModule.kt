package com.sentinel.app.core.di

import com.sentinel.app.data.haptic.HapticRepositoryImpl
import com.sentinel.app.data.radar.RadarRepositoryImpl
import com.sentinel.app.domain.repository.HapticRepository
import com.sentinel.app.domain.repository.RadarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt que vincula las interfaces de repositorio
 * con sus implementaciones concretas.
 *
 * La inyección de dependencias permite intercambiar implementaciones
 * en tests (ej. FakeRadarRepository con datos simulados).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRadarRepository(impl: RadarRepositoryImpl): RadarRepository

    @Binds
    @Singleton
    abstract fun bindHapticRepository(impl: HapticRepositoryImpl): HapticRepository
}
