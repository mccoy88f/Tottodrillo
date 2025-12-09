package com.tottodrillo.presentation.settings

import com.tottodrillo.domain.service.SourceServices
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * EntryPoint per accedere a SourceServices nelle schermate Compose
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface SourceServicesEntryPoint {
    fun sourceServices(): SourceServices
}

