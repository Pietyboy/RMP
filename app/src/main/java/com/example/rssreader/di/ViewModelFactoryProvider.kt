package com.example.rssreader.di

import com.example.rssreader.fit.FitApiService
import com.example.rssreader.fit.FitRepository
import com.example.rssreader.fit.FitRepositoryImpl
import com.example.rssreader.ui.ActivityViewModel

/**
 * Simple dependency injection provider for ViewModels
 */
object ViewModelFactoryProvider {
    
    // Lazily initialize the FitApiService
    private val fitApiService by lazy {
        FitApiService()
    }
    
    // Lazily initialize the FitRepository
    private val fitRepository: FitRepository by lazy {
        FitRepositoryImpl(fitApiService)
    }
    
    /**
     * Provides a factory for creating ActivityViewModel with dependencies
     */
    fun provideActivityViewModelFactory(): ActivityViewModel.Factory {
        return ActivityViewModel.Factory(fitRepository)
    }
} 