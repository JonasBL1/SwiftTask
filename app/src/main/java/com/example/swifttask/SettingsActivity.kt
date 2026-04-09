package com.example.swifttask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnCerrarSesion = findViewById<MaterialButton>(R.id.btnCerrarSesion)
        val radioGroupTheme = findViewById<RadioGroup>(R.id.radioGroupTheme)
        val tvTotalCompletadas = findViewById<TextView>(R.id.tvTotalCompletadas)
        val tvTotalPendientes = findViewById<TextView>(R.id.tvTotalPendientes)
        
        // Perfil
        val ivUserProfile = findViewById<ImageView>(R.id.ivUserProfile)
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)
        val tvSyncStatus = findViewById<TextView>(R.id.tvSyncStatus)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            tvUserName.text = user.displayName ?: "Usuario"
            tvUserEmail.text = user.email
            
            // Cargar foto de perfil con Glide
            user.photoUrl?.let {
                Glide.with(this)
                    .load(it)
                    .placeholder(R.drawable.ic_user)
                    .circleCrop()
                    .into(ivUserProfile)
            }
        }

        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentTheme = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Cargar Estadísticas desde Firebase
        val userId = user?.uid
        if (userId != null) {
            tvSyncStatus.text = getString(R.string.sincronizando)
            tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B")) // Ambar

            val dbRef = FirebaseDatabase.getInstance().getReference("Usuarios")
                .child(userId).child("Tareas")
            
            dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var completadas = 0
                    var pendientes = 0
                    for (tareaSnapshot in snapshot.children) {
                        val completada = tareaSnapshot.child("completada").getValue(Boolean::class.java) ?: false
                        if (completada) completadas++ else pendientes++
                    }
                    tvTotalCompletadas.text = completadas.toString()
                    tvTotalPendientes.text = pendientes.toString()
                    
                    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val hora = sdf.format(java.util.Date())
                    tvSyncStatus.text = getString(R.string.ultima_sincronizacion, hora)
                    tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#10B981")) // Verde
                }
                override fun onCancelled(error: DatabaseError) {
                    tvSyncStatus.text = getString(R.string.error_sincronizacion)
                    tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#EF4444")) // Rojo
                }
            })
        }

        // Set initial state
        when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> findViewById<RadioButton>(R.id.radioLight).isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> findViewById<RadioButton>(R.id.radioDark).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSystem).isChecked = true
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnCerrarSesion.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            sharedPreferences.edit().putInt("theme_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}