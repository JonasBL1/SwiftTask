package com.example.swifttask

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class SwiftTaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Cargar el tema guardado al iniciar la aplicación
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val mode = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        AppCompatDelegate.setDefaultNightMode(mode)

        // Activar persistencia Offline de Firebase
        com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}