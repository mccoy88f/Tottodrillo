package com.crocdb.friends.di

import com.crocdb.friends.data.repository.RomRepositoryImpl
import com.crocdb.friends.domain.repository.RomRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modulo Hilt per fornire le implementazioni dei Repository
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRomRepository(
        romRepositoryImpl: RomRepositoryImpl
    ): RomRepository
}
