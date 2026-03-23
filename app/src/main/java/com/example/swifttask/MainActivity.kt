package com.example.swifttask

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: TareaAdapter
    private val listaTareas = mutableListOf<Tarea>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicializar Firebase (Apunta a la tabla "Tareas")
        dbRef = FirebaseDatabase.getInstance().getReference("Tareas")

        val etTarea = findViewById<EditText>(R.id.etTarea)
        val btnAgregar = findViewById<Button>(R.id.btnAgregar)
        val rvTareas = findViewById<RecyclerView>(R.id.rvTareas)

        // 2. Configurar el RecyclerView
        adapter = TareaAdapter(
            listaTareas,
            onEliminarClick = { tarea -> eliminarTarea(tarea) },
            onEstadoClick = { tarea -> actualizarTarea(tarea) }
        )
        rvTareas.layoutManager = LinearLayoutManager(this)
        rvTareas.adapter = adapter

        // 3. Evento del botón Agregar
        btnAgregar.setOnClickListener {
            val nombre = etTarea.text.toString()
            if (nombre.isNotEmpty()) {
                guardarTareaEnFirebase(nombre)
                etTarea.text.clear()
            } else {
                Toast.makeText(this, "Escribe algo primero", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Leer tareas de Firebase en tiempo real
        obtenerTareasDeFirebase()
    }

    private fun guardarTareaEnFirebase(nombre: String) {
        val id = dbRef.push().key ?: return
        val nuevaTarea = Tarea(id, nombre, false)
        dbRef.child(id).setValue(nuevaTarea)
    }

    private fun obtenerTareasDeFirebase() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaTareas.clear()
                for (tareaSnapshot in snapshot.children) {
                    val tarea = tareaSnapshot.getValue(Tarea::class.java)
                    tarea?.let { listaTareas.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun eliminarTarea(tarea: Tarea) {
        tarea.id?.let { dbRef.child(it).removeValue() }
    }

    private fun actualizarTarea(tarea: Tarea) {
        tarea.id?.let { dbRef.child(it).setValue(tarea) }
    }
}