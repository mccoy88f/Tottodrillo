package com.tottodrillo.presentation.settings

import com.tottodrillo.domain.manager.SourceUpdateManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * EntryPoint per accedere a SourceUpdateManager nelle schermate Compose
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface SourceUpdateManagerEntryPoint {
    fun sourceUpdateManager(): SourceUpdateManager
}

