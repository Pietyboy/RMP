package com.example.rssreader.di

import com.example.rssreader.fit.FitApiService
import com.example.rssreader.fit.FitRepository
import com.example.rssreader.fit.FitRepositoryImpl
import com.example.rssreader.ui.ActivityViewModel

object ViewModelFactoryProvider {
    
    private val fitApiService by lazy {
        FitApiService()
    }
    
    private val fitRepository: FitRepository by lazy {
        FitRepositoryImpl(fitApiService)
    }

    fun provideActivityViewModelFactory(): ActivityViewModel.Factory {
        return ActivityViewModel.Factory(fitRepository)
    }
} 