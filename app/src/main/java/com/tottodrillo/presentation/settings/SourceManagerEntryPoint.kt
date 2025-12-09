package com.tottodrillo.presentation.settings

import com.tottodrillo.domain.manager.SourceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * EntryPoint per accedere a SourceManager nelle schermate Compose
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface SourceManagerEntryPoint {
    fun sourceManager(): SourceManager
}

