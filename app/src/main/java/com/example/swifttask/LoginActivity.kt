package com.example.swifttask

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var progressBar: ProgressBar

    // Nuevo Launcher para Google Sign-In (Reemplaza startActivityForResult)
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error de Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            progressBar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        progressBar = findViewById(R.id.pbLoading)

        // Configurar Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnLogin = findViewById<Button>(R.id.btnIniciarSesion)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogleLogin)
        val etEmail = findViewById<EditText>(R.id.etEmailLogin)
        val etPass = findViewById<EditText>(R.id.etPasswordLogin)
        val tvIrARegistro = findViewById<TextView>(R.id.tvIrARegistro)

        btnGoogle.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                btnLogin.isEnabled = false
                
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this) { task ->
                        progressBar.visibility = View.GONE
                        btnLogin.isEnabled = true
                        
                        if (task.isSuccessful) {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        tvIrARegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error de Firebase: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}