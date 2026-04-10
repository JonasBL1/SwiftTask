package com.example.swifttask

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializamos Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Enlazamos los componentes con los IDs de tu XML
        val btnCrearCuenta = findViewById<Button>(R.id.btnCrearCuenta)
        val etNombre = findViewById<EditText>(R.id.etNombreCompleto)
        val etEmail = findViewById<EditText>(R.id.etEmailRegistro)
        val etPass = findViewById<EditText>(R.id.etPasswordRegistro)
        val tvIrALogin = findViewById<TextView>(R.id.tvIrALogin)

        tvIrALogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnCrearCuenta.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val nombre = etNombre.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty() && nombre.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // Guardar el nombre en el perfil del usuario
                            val user = auth.currentUser
                            val profileUpdates = userProfileChangeRequest {
                                displayName = nombre
                            }

                            user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                // Una vez actualizado el nombre, vamos al Main
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}