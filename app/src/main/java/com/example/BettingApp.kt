package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.BettingRepository

class BettingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: BettingRepository by lazy { BettingRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
    }
}
