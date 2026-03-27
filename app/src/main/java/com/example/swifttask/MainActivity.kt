package com.example.swifttask

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: TareaAdapter
    private val listaTareas = mutableListOf<Tarea>()
    private var asignaturaSeleccionada = "Personal"
    private var fechaSeleccionada = "Sin fecha"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Configurar la Barra Superior (Toolbar)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 2. Inicializar Firebase
        dbRef = FirebaseDatabase.getInstance().getReference("Tareas")

        // 3. Referencias de la interfaz
        val rvTareas = findViewById<RecyclerView>(R.id.rvTareas)
        val fabAgregar = findViewById<FloatingActionButton>(R.id.fabAgregar)

        // 4. Configurar el Adapter y RecyclerView
        adapter = TareaAdapter(
            listaTareas,
            onEliminarClick = { tarea -> eliminarTarea(tarea) },
            onEstadoClick = { tarea -> actualizarTarea(tarea) }
        )
        rvTareas.layoutManager = LinearLayoutManager(this)
        rvTareas.adapter = adapter

        // 5. Eventos
        fabAgregar.setOnClickListener {
            mostrarDialogoNuevaTarea()
        }

        obtenerTareasDeFirebase()
    }

    private fun guardarTareaReal(nombre: String, asignatura: String, fecha: String) {
        val id = dbRef.push().key ?: return
        val nuevaTarea = Tarea(
            id = id,
            titulo = nombre,
            asignatura = asignatura,
            fechaLimite = fecha,
            esRepetitiva = false,
            completada = false
        )
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

                // Ordenar: Pendientes arriba, completadas abajo
                listaTareas.sortBy { it.completada }
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

    private fun mostrarDialogoNuevaTarea() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_add_tarea, null)
        dialog.setContentView(view)

        val etTitulo = view.findViewById<android.widget.EditText>(R.id.etNuevoTitulo)
        val btnAsignatura = view.findViewById<android.widget.Button>(R.id.btnSeleccionarAsignatura)
        val btnFecha = view.findViewById<android.widget.Button>(R.id.btnSeleccionarFecha)
        val btnGuardar = view.findViewById<android.widget.Button>(R.id.btnGuardarTarea)

        btnAsignatura.setOnClickListener {
            val popup = android.widget.PopupMenu(this, btnAsignatura)
            val asignaturas = listOf(
                "Matemáticas", "Español", "Física", "Química",
                "Historia", "Inglés", "Programación", "Personal"
            )
            asignaturas.forEach { nombre -> popup.menu.add(nombre) }

            popup.setOnMenuItemClickListener { item ->
                asignaturaSeleccionada = item.title.toString()
                btnAsignatura.text = asignaturaSeleccionada
                true
            }
            popup.show()
        }

        btnFecha.setOnClickListener {
            val calendario = java.util.Calendar.getInstance()
            val year = calendario.get(java.util.Calendar.YEAR)
            val month = calendario.get(java.util.Calendar.MONTH)
            val day = calendario.get(java.util.Calendar.DAY_OF_MONTH)

            val dpd = android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                fechaSeleccionada = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                btnFecha.text = fechaSeleccionada
            }, year, month, day)

            dpd.show()
        }

        btnGuardar.setOnClickListener {
            val nombre = etTitulo.text.toString().trim()
            if (nombre.isNotEmpty()) {
                guardarTareaReal(nombre, asignaturaSeleccionada, fechaSeleccionada)
                dialog.dismiss()
                asignaturaSeleccionada = "Personal"
                fechaSeleccionada = "Sin fecha"
            } else {
                Toast.makeText(this, "Escribe un título", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}