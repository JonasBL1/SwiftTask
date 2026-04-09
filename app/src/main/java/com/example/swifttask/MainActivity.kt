package com.example.swifttask

import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter


class MainActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: TareaAdapter

    // Listas para manejar los filtros
    private val listaTareasCompleta = mutableListOf<Tarea>()
    private val listaTareasVisibles = mutableListOf<Tarea>()

    private var asignaturaSeleccionada = "Personal"
    private var fechaSeleccionada = "Sin fecha"
    private var prioridadSeleccionada = "Media"
    private var filtroActual = "Todas"
    private var criterioOrdenacion = "Prioridad"
    private val listaCategorias = mutableListOf<Categoria>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. UI Setup
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val userId = user?.uid

        // 2. Firebase
        dbRef = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(userId ?: "anonimo")
            .child("Tareas")

        // 3. View Binding
        val rvTareas = findViewById<RecyclerView>(R.id.rvTareas)
        val fabAgregar = findViewById<FloatingActionButton>(R.id.fabAgregar)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFiltros)
        val tvTareaCount = findViewById<android.widget.TextView>(R.id.tvTareaCount)
        val btnSettings = findViewById<android.view.View>(R.id.btnSettings)
        val btnMenu = findViewById<android.view.View>(R.id.btnMenu)
        val btnProfile = findViewById<android.view.View>(R.id.btnProfile)
        val ivProfile = findViewById<android.widget.ImageView>(R.id.btnProfileImage) // Asumiendo que añadimos el ID

        if (user != null) {
            user.photoUrl?.let {
                com.bumptech.glide.Glide.with(this)
                    .load(it)
                    .placeholder(R.drawable.ic_user)
                    .circleCrop()
                    .into(ivProfile)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnMenu.setOnClickListener {
            mostrarMenuOrdenacion(it)
        }

        btnProfile.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            val email = user?.email ?: "Usuario"
            Toast.makeText(this, getString(R.string.sesion_de, email), Toast.LENGTH_SHORT).show()
        }

        // 4. Adapter
        adapter = TareaAdapter(
            listaTareasVisibles,
            onEliminarClick = { tarea -> eliminarTarea(tarea) },
            onEstadoClick = { tarea -> actualizarTarea(tarea) },
            onTareaClick = { tarea -> mostrarDialogoNuevaTarea(tarea) }
        )
        rvTareas.layoutManager = LinearLayoutManager(this)
        rvTareas.adapter = adapter

        // Configurar Swipe
        configurarSwipe(rvTareas)

        // 5. Filtros - Eliminamos la lógica estática para que la maneje actualizarChipsCategorias()
        fabAgregar.setOnClickListener {
            mostrarDialogoNuevaTarea()
        }

        obtenerCategoriasDeFirebase()
        obtenerTareasDeFirebase()
    }

    private fun obtenerCategoriasDeFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val catRef = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(userId ?: "anonimo")
            .child("Categorias")

        catRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaCategorias.clear()
                // Añadir categorías por defecto si no existen o siempre como base
                listaCategorias.add(Categoria("todas", "Todas"))
                
                for (catSnapshot in snapshot.children) {
                    val categoria = catSnapshot.getValue(Categoria::class.java)
                    if (categoria != null) {
                        listaCategorias.add(categoria)
                    }
                }
                
                // Si está vacío (primer inicio), crear las de defecto en Firebase
                if (snapshot.childrenCount == 0L) {
                    crearCategoriasPorDefecto()
                } else {
                    actualizarChipsCategorias()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun crearCategoriasPorDefecto() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val catRef = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(userId ?: "anonimo")
            .child("Categorias")

        val cat1 = Categoria(catRef.push().key ?: "1", "Universidad", "#3B82F6")
        val cat2 = Categoria(catRef.push().key ?: "2", "Personal", "#10B981")
        
        catRef.child(cat1.id).setValue(cat1)
        catRef.child(cat2.id).setValue(cat2)
    }

    private fun actualizarChipsCategorias() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFiltros)
        val selectedText = filtroActual
        chipGroup.removeAllViews()

        for (categoria in listaCategorias) {
            val chip = com.google.android.material.chip.Chip(this)
            chip.text = categoria.nombre
            chip.isCheckable = true
            chip.isClickable = true
            
            // Estilo
            chip.setChipBackgroundColorResource(android.R.color.transparent)
            val strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E2E8F0"))
            chip.chipStrokeColor = strokeColor
            chip.chipStrokeWidth = 2f
            chip.setTextColor(getColor(R.color.text_secondary))
            
            if (categoria.nombre == selectedText) {
                chip.isChecked = true
                chip.setChipBackgroundColorResource(R.color.azul_primario)
                chip.setTextColor(getColor(R.color.white))
                chip.chipStrokeWidth = 0f
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    filtroActual = categoria.nombre
                    aplicarFiltro()
                    actualizarChipsCategorias() // Para refrescar visualmente el estilo
                }
            }
            chipGroup.addView(chip)
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // Si no hay sesión, mandamos al usuario al Login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun aplicarFiltro() {
        listaTareasVisibles.clear()

        if (filtroActual == "Todas") {
            listaTareasVisibles.addAll(listaTareasCompleta)
        } else {
            val filtradas = listaTareasCompleta.filter { it.asignatura == filtroActual }
            listaTareasVisibles.addAll(filtradas)
        }

        ordenarTareas()
        adapter.updateLista(listaTareasVisibles)

        actualizarContador()
    }

    private fun ordenarTareas() {
        when (criterioOrdenacion) {
            "Prioridad" -> {
                val prioridadMap = mapOf("Alta" to 1, "Media" to 2, "Baja" to 3)
                listaTareasVisibles.sortWith(compareBy<Tarea> { it.completada }.thenBy { prioridadMap[it.prioridad] ?: 4 })
            }
            "Nombre" -> {
                listaTareasVisibles.sortWith(compareBy<Tarea> { it.completada }.thenBy { it.titulo.lowercase() })
            }
        }
    }

    private fun mostrarMenuOrdenacion(view: android.view.View) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menu.add(getString(R.string.orden_prioridad))
        popup.menu.add(getString(R.string.orden_nombre))

        popup.setOnMenuItemClickListener { item ->
            criterioOrdenacion = when (item.title) {
                getString(R.string.orden_prioridad) -> "Prioridad"
                getString(R.string.orden_nombre) -> "Nombre"
                else -> "Prioridad"
            }
            aplicarFiltro()
            true
        }
        popup.show()
    }

    private fun actualizarContador() {
        val total = listaTareasVisibles.size
        val completadas = listaTareasVisibles.count { it.completada }
        val pendientes = total - completadas
        
        val tvTareaCount = findViewById<android.widget.TextView>(R.id.tvTareaCount)
        tvTareaCount?.text = getString(R.string.tareas_pendientes, pendientes)

        // Actualizar Barra de Progreso
        val progressTareas = findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressTareas)
        val tvPorcentaje = findViewById<android.widget.TextView>(R.id.tvPorcentaje)
        
        if (total > 0) {
            val porcentaje = (completadas * 100) / total
            val anteriorPorcentaje = progressTareas?.progress ?: 0
            
            progressTareas?.setProgress(porcentaje, true)
            tvPorcentaje?.text = getString(R.string.porcentaje_progreso, porcentaje)

            // DISPARAR CONFETI si llegamos al 100% y antes no estábamos ahí
            if (porcentaje == 100 && anteriorPorcentaje < 100) {
                dispararConfeti()
            }
        } else {
            progressTareas?.setProgress(0, true)
            tvPorcentaje?.text = "0%"
        }

        val layoutEmptyState = findViewById<android.view.View>(R.id.layoutEmptyState)
        val rvTareas = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTareas)

        if (listaTareasVisibles.isEmpty()) {
            layoutEmptyState?.visibility = android.view.View.VISIBLE
            rvTareas?.visibility = android.view.View.GONE
        } else {
            layoutEmptyState?.visibility = android.view.View.GONE
            rvTareas?.visibility = android.view.View.VISIBLE
        }
    }

    private fun configurarSwipe(recyclerView: RecyclerView) {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val posicion = viewHolder.adapterPosition
                val tarea = listaTareasVisibles[posicion]

                // Vibración sutil al completar el swipe
                viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                if (direction == ItemTouchHelper.RIGHT) {
                    // Swipe Derecha: Completar/Descompletar
                    tarea.completada = !tarea.completada
                    actualizarTarea(tarea)
                    val mensaje = if (tarea.completada) R.string.tarea_completada else R.string.tarea_pendiente
                    Snackbar.make(recyclerView, mensaje, Snackbar.LENGTH_SHORT).show()
                } else {
                    // Swipe Izquierda: Eliminar con opción de deshacer
                    val tareaEliminada = tarea
                    eliminarTarea(tarea)
                    
                    Snackbar.make(recyclerView, R.string.tarea_eliminada, Snackbar.LENGTH_LONG)
                        .setAction(R.string.deshacer) {
                            tareaEliminada.id?.let { id ->
                                dbRef.child(id).setValue(tareaEliminada)
                            }
                        }.show()
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val p = android.graphics.Paint()

                if (dX > 0) { // Swipe Derecha (Completar - Verde)
                    p.color = android.graphics.Color.parseColor("#10B981")
                    c.drawRect(
                        itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left.toFloat() + dX, itemView.bottom.toFloat(), p
                    )
                } else if (dX < 0) { // Swipe Izquierda (Eliminar - Rojo)
                    p.color = android.graphics.Color.parseColor("#EF4444")
                    c.drawRect(
                        itemView.right.toFloat() + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat(), p
                    )
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun obtenerTareasDeFirebase() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaTareasCompleta.clear()
                for (tareaSnapshot in snapshot.children) {
                    val tarea = tareaSnapshot.getValue(Tarea::class.java)
                    tarea?.let { listaTareasCompleta.add(it) }
                }
                aplicarFiltro()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, getString(R.string.error_firebase, error.message), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun guardarTareaReal(nombre: String, asignatura: String, fecha: String, prioridad: String) {
        val id = dbRef.push().key ?: return
        val nuevaTarea = Tarea(
            id = id,
            titulo = nombre,
            asignatura = asignatura,
            fechaLimite = fecha,
            prioridad = prioridad,
            esRepetitiva = false,
            completada = false
        )
        dbRef.child(id).setValue(nuevaTarea)
        programarNotificacion(nuevaTarea)
    }

    private fun eliminarTarea(tarea: Tarea) {
        tarea.id?.let { dbRef.child(it).removeValue() }
    }

    private fun actualizarTarea(tarea: Tarea) {
        tarea.id?.let { 
            dbRef.child(it).setValue(tarea)
            if (!tarea.completada) {
                programarNotificacion(tarea)
            } else {
                cancelarNotificacion(it)
            }
        }
    }

    private fun programarNotificacion(tarea: Tarea) {
        if (tarea.fechaLimite == getString(R.string.sin_fecha) || tarea.id == null) return

        try {
            val sdf = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
            val fechaLimite = sdf.parse(tarea.fechaLimite) ?: return
            
            // Programar para las 8:00 AM del día de vencimiento
            val calendar = Calendar.getInstance().apply {
                time = fechaLimite
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
            }

            val delay = calendar.timeInMillis - System.currentTimeMillis()
            if (delay < 0) return // Ya pasó la hora de la notificación

            val data = Data.Builder()
                .putString("titulo", tarea.titulo)
                .putString("id", tarea.id)
                .build()

            val notificationRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tarea.id)
                .build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                tarea.id,
                ExistingWorkPolicy.REPLACE,
                notificationRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelarNotificacion(tareaId: String) {
        WorkManager.getInstance(this).cancelUniqueWork(tareaId)
    }

    private fun dispararConfeti() {
        val konfettiView = findViewById<nl.dionsegijn.konfetti.xml.KonfettiView>(R.id.konfettiView)
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a.toInt(), 0xff726d.toInt(), 0xf4306d.toInt(), 0xb48def.toInt()),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
            position = Position.Relative(0.5, 0.3)
        )
        konfettiView.start(party)
    }

    private fun mostrarDialogoNuevaCategoria(btnAsignatura: android.widget.Button) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Nueva Categoría")

        val input = android.widget.EditText(this)
        input.hint = "Ej. Gimnasio, Trabajo..."
        input.setPadding(50, 40, 50, 40)
        builder.setView(input)

        builder.setPositiveButton("Crear") { _, _ ->
            val nombreCat = input.text.toString().trim()
            if (nombreCat.isNotEmpty()) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val catRef = FirebaseDatabase.getInstance().getReference("Usuarios")
                    .child(userId ?: "anonimo")
                    .child("Categorias")
                
                val key = catRef.push().key ?: return@setPositiveButton
                val nuevaCat = Categoria(key, nombreCat, "#3B82F6")
                
                catRef.child(key).setValue(nuevaCat).addOnSuccessListener {
                    asignaturaSeleccionada = nombreCat
                    btnAsignatura.text = nombreCat
                    Toast.makeText(this, "Categoría creada", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun mostrarDialogoNuevaTarea(tareaAEditar: Tarea? = null) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_add_tarea, null)
        dialog.setContentView(view)

        val tvTituloPanel = view.findViewById<android.widget.TextView>(R.id.tvTituloPanel)
        val etTitulo = view.findViewById<android.widget.EditText>(R.id.etNuevoTitulo)
        val btnAsignatura = view.findViewById<android.widget.Button>(R.id.btnSeleccionarAsignatura)
        val btnPrioridad = view.findViewById<android.widget.Button>(R.id.btnSeleccionarPrioridad)
        val btnFecha = view.findViewById<android.widget.Button>(R.id.btnSeleccionarFecha)
        val btnGuardar = view.findViewById<android.widget.Button>(R.id.btnGuardarTarea)
        val btnClose = view.findViewById<android.view.View>(R.id.btnCloseDialog)

        btnClose.setOnClickListener { dialog.dismiss() }

        // Si estamos editando, rellenamos los campos
        if (tareaAEditar != null) {
            tvTituloPanel.text = getString(R.string.editar_tarea)
            etTitulo.setText(tareaAEditar.titulo)
            btnAsignatura.text = tareaAEditar.asignatura
            btnPrioridad.text = tareaAEditar.prioridad
            btnFecha.text = tareaAEditar.fechaLimite
            btnGuardar.text = getString(R.string.actualizar)
            
            asignaturaSeleccionada = tareaAEditar.asignatura
            prioridadSeleccionada = tareaAEditar.prioridad
            fechaSeleccionada = tareaAEditar.fechaLimite
        } else {
            tvTituloPanel.text = getString(R.string.agregar_tarea)
            btnGuardar.text = getString(R.string.guardar)
            // Resetear valores para nueva tarea
            asignaturaSeleccionada = getString(R.string.personal)
            prioridadSeleccionada = getString(R.string.media)
            fechaSeleccionada = getString(R.string.sin_fecha)
        }

        btnAsignatura.setOnClickListener {
            val popup = android.widget.PopupMenu(this, btnAsignatura)
            
            // Usar categorías dinámicas (excluyendo "Todas")
            val nombresCategorias = listaCategorias
                .filter { it.nombre != "Todas" }
                .map { it.nombre }
                .toMutableList()
            
            nombresCategorias.add("+ Nueva Categoría...")
            
            nombresCategorias.forEach { nombre -> popup.menu.add(nombre) }

            popup.setOnMenuItemClickListener { item ->
                if (item.title == "+ Nueva Categoría...") {
                    mostrarDialogoNuevaCategoria(btnAsignatura)
                } else {
                    asignaturaSeleccionada = item.title.toString()
                    btnAsignatura.text = asignaturaSeleccionada
                }
                true
            }
            popup.show()
        }

        btnPrioridad.setOnClickListener {
            val popup = android.widget.PopupMenu(this, btnPrioridad)
            val prioridades = listOf(getString(R.string.alta), getString(R.string.media), getString(R.string.baja))
            prioridades.forEach { p -> popup.menu.add(p) }

            popup.setOnMenuItemClickListener { item ->
                prioridadSeleccionada = item.title.toString()
                btnPrioridad.text = prioridadSeleccionada
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
                if (tareaAEditar == null) {
                    guardarTareaReal(nombre, asignaturaSeleccionada, fechaSeleccionada, prioridadSeleccionada)
                } else {
                    val tareaActualizada = tareaAEditar.copy(
                        titulo = nombre,
                        asignatura = asignaturaSeleccionada,
                        fechaLimite = fechaSeleccionada,
                        prioridad = prioridadSeleccionada
                    )
                    actualizarTarea(tareaActualizada)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, getString(R.string.escribe_un_titulo), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
} // Cierra MainActivity
