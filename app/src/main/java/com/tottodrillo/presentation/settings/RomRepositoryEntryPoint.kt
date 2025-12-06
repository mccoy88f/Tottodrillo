package com.tottodrillo.presentation.settings

import com.tottodrillo.domain.repository.RomRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * Entry point per accedere a RomRepository in Composables non iniettati
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface RomRepositoryEntryPoint {
    fun romRepository(): RomRepository
}

