package com.example

import android.app.Application
import com.example.data.local.TonalFolioDatabase
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.LibraryRepository

class TonalFolioApp : Application() {
    val database: TonalFolioDatabase by lazy { TonalFolioDatabase.getInstance(this) }
    val preferencesRepository: AppPreferencesRepository by lazy { AppPreferencesRepository(this) }
    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            context = this,
            dao = database.libraryDao(),
            prefsRepo = preferencesRepository
        )
    }
}
