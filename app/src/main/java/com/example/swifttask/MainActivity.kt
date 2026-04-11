package com.example.swifttask

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Button
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.PendingIntent
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import android.text.TextWatcher
import android.text.Editable
import android.content.res.ColorStateList
import android.provider.Settings

class MainActivity : AppCompatActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: TareaAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "¡Notificaciones activadas!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No recibirás recordatorios sin el permiso", Toast.LENGTH_LONG).show()
        }
    }

    // Listas para manejar los filtros
    private val listaTareasCompleta = mutableListOf<Tarea>()
    private val listaTareasVisibles = mutableListOf<Tarea>()

    private var categoriaSeleccionada = "Personal"
    private var fechaSeleccionada = "Sin fecha"
    private var horaSeleccionada = "08:00"
    private var recordatorioActivado = false
    private var prioridadSeleccionada = "Media"
    private var filtroActual = "Todas"
    private var criterioOrdenacion = "Prioridad"
    private var queryBusqueda = ""
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
        val btnSettings = findViewById<View>(R.id.btnSettings)
        val btnMenu = findViewById<View>(R.id.btnMenu)
        val btnProfile = findViewById<View>(R.id.btnProfile)
        val ivProfile = findViewById<ImageView>(R.id.btnProfileImage)
        val etBuscar = findViewById<EditText>(R.id.etBuscar)

        // Solicitar permisos de notificación (Android 13+)
        solicitarPermisosNotificacion()
        crearCanalNotificaciones()

        if (user != null) {
            user.photoUrl?.let {
                Glide.with(this)
                    .load(it)
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(120, 120) // Forcing higher resolution on resize
                    .into(ivProfile)
            }
        }

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                queryBusqueda = s.toString().trim()
                aplicarFiltro()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnMenu.setOnClickListener {
            mostrarMenuOrdenacion(it)
        }

        btnProfile.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val email = currentUser?.email ?: "Usuario"
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
                listaCategorias.add(Categoria("todas", "Todas"))
                
                for (catSnapshot in snapshot.children) {
                    val categoria = catSnapshot.getValue(Categoria::class.java)
                    if (categoria != null) {
                        listaCategorias.add(categoria)
                    }
                }
                
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

        val cat1 = Categoria(catRef.push().key ?: "1", "Personal", "#3B82F6")
        val cat2 = Categoria(catRef.push().key ?: "2", "Universidad", "#8B5CF6")

        catRef.child(cat1.id).setValue(cat1)
        catRef.child(cat2.id).setValue(cat2)
    }

    private fun actualizarChipsCategorias() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFiltros)
        val selectedText = filtroActual
        chipGroup.removeAllViews()

        for (categoria in listaCategorias) {
            val chip = Chip(this)
            chip.text = categoria.nombre
            chip.isCheckable = true
            chip.isClickable = true
            
            chip.setChipBackgroundColorResource(android.R.color.transparent)
            val strokeColor = ColorStateList.valueOf("#E2E8F0".toColorInt())
            chip.chipStrokeColor = strokeColor
            chip.chipStrokeWidth = 2f
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            
            if (categoria.nombre == selectedText) {
                chip.isChecked = true
                chip.setChipBackgroundColorResource(R.color.azul_primario)
                chip.setTextColor(ContextCompat.getColor(this, R.color.white))
                chip.chipStrokeWidth = 0f
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    filtroActual = categoria.nombre
                    aplicarFiltro()
                    actualizarChipsCategorias()
                }
            }

            chip.setOnLongClickListener {
                if (categoria.nombre != "Todas") {
                    mostrarDialogoEditarCategoria(categoria)
                }
                true
            }
            chipGroup.addView(chip)
        }
    }

    override fun onStart() {
        super.onStart()
        aplicarFiltro()
    }

    private fun aplicarFiltro() {
        listaTareasVisibles.clear()
        
        val filtradas = if (filtroActual == "Todas") {
            listaTareasCompleta
        } else {
            listaTareasCompleta.filter { it.categoria == filtroActual }
        }

        val busqueda = if (queryBusqueda.isEmpty()) {
            filtradas
        } else {
            filtradas.filter { it.titulo.contains(queryBusqueda, ignoreCase = true) }
        }

        listaTareasVisibles.addAll(busqueda)
        ordenarTareas()
        actualizarContador()
        
        val layoutEmptyState = findViewById<View>(R.id.layoutEmptyState)
        val rvTareas = findViewById<View>(R.id.rvTareas)
        if (listaTareasVisibles.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvTareas.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvTareas.visibility = View.VISIBLE
        }
    }

    private fun ordenarTareas() {
        when (criterioOrdenacion) {
            "Prioridad" -> {
                val orden = mapOf("Alta" to 1, "Media" to 2, "Baja" to 3)
                listaTareasVisibles.sortBy { orden[it.prioridad] ?: 4 }
            }
            "Fecha" -> {
                listaTareasVisibles.sortBy { it.fechaLimite }
            }
            "Nombre" -> {
                listaTareasVisibles.sortBy { it.titulo.lowercase() }
            }
        }
        adapter.updateLista(listaTareasVisibles)
    }

    private fun mostrarMenuOrdenacion(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Prioridad")
        popup.menu.add("Fecha")
        popup.menu.add("Nombre")

        popup.setOnMenuItemClickListener { item ->
            criterioOrdenacion = item.title.toString()
            ordenarTareas()
            true
        }
        popup.show()
    }

    private fun actualizarContador() {
        val tvTareaCount = findViewById<TextView>(R.id.tvTareaCount)
        val progressTareas = findViewById<LinearProgressIndicator>(R.id.progressTareas)
        val tvPorcentaje = findViewById<TextView>(R.id.tvPorcentaje)

        val total = listaTareasVisibles.size
        val completadas = listaTareasVisibles.count { it.completada }
        
        if (total == 0) {
            tvTareaCount.text = getString(R.string.no_hay_tareas)
            progressTareas.progress = 0
            tvPorcentaje.text = "0%"
        } else {
            tvTareaCount.text = getString(R.string.tareas_completadas, completadas, total)
            val porcentaje = if (total > 0) (completadas * 100) / total else 0
            progressTareas.progress = porcentaje
            tvPorcentaje.text = getString(R.string.porcentaje_progreso, porcentaje)
            
            if (completadas == total) {
                dispararConfeti()
            }
        }
    }

    private fun configurarSwipe(recyclerView: RecyclerView) {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val tarea = listaTareasVisibles[position]

                if (direction == ItemTouchHelper.RIGHT) {
                    // Completar / Descompletar
                    val tareaActualizada = tarea.copy(completada = !tarea.completada)
                    actualizarTarea(tareaActualizada)
                } else {
                    // Eliminar
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.eliminar_tarea_tit)
                        .setMessage(R.string.confirmar_eliminar)
                        .setPositiveButton(R.string.eliminar) { _, _ ->
                            eliminarTarea(tarea)
                        }
                        .setNegativeButton(R.string.cancelar) { _, _ ->
                            adapter.notifyItemChanged(position)
                        }
                        .show()
                }
            }

            override fun onChildDraw(canvas: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                // Implementación opcional de fondo de color al deslizar
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
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

    private fun guardarTareaReal(nombre: String, categoria: String, fecha: String, hora: String, recordatorio: Boolean, prioridad: String) {
        val id = dbRef.push().key ?: return
        val nuevaTarea = Tarea(
            id = id,
            titulo = nombre,
            categoria = categoria,
            fechaLimite = fecha,
            horaLimite = hora,
            recordatorioActivado = recordatorio,
            prioridad = prioridad,
            esRepetitiva = false,
            completada = false
        )
        dbRef.child(id).setValue(nuevaTarea)
        if (recordatorio) {
            programarNotificacion(nuevaTarea)
        }
    }

    private fun eliminarTarea(tarea: Tarea) {
        tarea.id?.let { 
            dbRef.child(it).removeValue()
            cancelarNotificacion(it)
        }
    }

    private fun actualizarTarea(tarea: Tarea) {
        tarea.id?.let {
            dbRef.child(it).setValue(tarea)
            if (tarea.recordatorioActivado && !tarea.completada) {
                programarNotificacion(tarea)
            } else {
                cancelarNotificacion(it)
            }
        }
    }

    private fun programarNotificacion(tarea: Tarea) {
        if (tarea.fechaLimite == getString(R.string.sin_fecha) || tarea.id == null || !tarea.recordatorioActivado) return

        try {
            val sdf = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
            val fechaHoraString = "${tarea.fechaLimite} ${tarea.horaLimite}"
            val fechaHoraLimite = sdf.parse(fechaHoraString) ?: return
            
            val calendar = Calendar.getInstance().apply {
                time = fechaHoraLimite
            }

            val triggerTime = calendar.timeInMillis
            if (triggerTime <= System.currentTimeMillis()) return

            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("titulo", tarea.titulo)
                putExtra("id", tarea.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                tarea.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            
            Log.d("ALARM", "Alarma programada para: $fechaHoraString")
            Toast.makeText(this, "Recordatorio programado", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelarNotificacion(tareaId: String) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            tareaId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
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

    private fun mostrarDialogoNuevaCategoria(btnCategoria: Button) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nueva Categoría")

        val input = EditText(this)
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
                    categoriaSeleccionada = nombreCat
                    btnCategoria.text = nombreCat
                    Toast.makeText(this, "Categoría creada", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun mostrarDialogoNuevaTarea(tareaAEditar: Tarea? = null) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_add_tarea, findViewById(android.R.id.content), false)
        dialog.setContentView(view)

        val tvTituloPanel = view.findViewById<TextView>(R.id.tvTituloPanel)
        val etTitulo = view.findViewById<EditText>(R.id.etNuevoTitulo)
        val btnCategoria = view.findViewById<Button>(R.id.btnSeleccionarCategoria)
        val btnPrioridad = view.findViewById<Button>(R.id.btnSeleccionarPrioridad)
        val btnFecha = view.findViewById<Button>(R.id.btnSeleccionarFecha)
        val btnHora = view.findViewById<Button>(R.id.btnSeleccionarHora)
        val switchRecordatorio = view.findViewById<MaterialSwitch>(R.id.switchRecordatorio)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarTarea)
        val btnClose = view.findViewById<View>(R.id.btnCloseDialog)

        btnClose.setOnClickListener { dialog.dismiss() }

        if (tareaAEditar != null) {
            tvTituloPanel.text = getString(R.string.editar_tarea)
            etTitulo.setText(tareaAEditar.titulo)
            btnCategoria.text = tareaAEditar.categoria
            btnPrioridad.text = tareaAEditar.prioridad
            btnFecha.text = tareaAEditar.fechaLimite
            btnHora.text = tareaAEditar.horaLimite
            switchRecordatorio.isChecked = tareaAEditar.recordatorioActivado
            btnGuardar.text = getString(R.string.actualizar)
            
            categoriaSeleccionada = tareaAEditar.categoria
            prioridadSeleccionada = tareaAEditar.prioridad
            fechaSeleccionada = tareaAEditar.fechaLimite
            horaSeleccionada = tareaAEditar.horaLimite
            recordatorioActivado = tareaAEditar.recordatorioActivado
        } else {
            tvTituloPanel.text = getString(R.string.agregar_tarea)
            btnGuardar.text = getString(R.string.guardar)
            categoriaSeleccionada = getString(R.string.personal)
            prioridadSeleccionada = getString(R.string.media)
            fechaSeleccionada = getString(R.string.sin_fecha)
            horaSeleccionada = "08:00"
            recordatorioActivado = false
            btnHora.text = horaSeleccionada
            switchRecordatorio.isChecked = false
        }

        btnCategoria.setOnClickListener {
            val popup = PopupMenu(this, btnCategoria)
            val categoriasParaMenu = listaCategorias.filter { it.nombre != "Todas" }
            for (cat in categoriasParaMenu) {
                popup.menu.add(cat.nombre)
            }
            popup.menu.add("+ Nueva Categoría...")
            popup.menu.add("⚙️ Editar Categorías...")
            popup.setOnMenuItemClickListener { item ->
                when (val title = item.title.toString()) {
                    "+ Nueva Categoría..." -> mostrarDialogoNuevaCategoria(btnCategoria)
                    "⚙️ Editar Categorías..." -> mostrarDialogoGestionCategorias()
                    else -> {
                        categoriaSeleccionada = title
                        btnCategoria.text = categoriaSeleccionada
                    }
                }
                true
            }
            popup.show()
        }

        btnPrioridad.setOnClickListener {
            val popup = PopupMenu(this, btnPrioridad)
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
            val calendario = Calendar.getInstance()
            val year = calendario.get(Calendar.YEAR)
            val month = calendario.get(Calendar.MONTH)
            val day = calendario.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                fechaSeleccionada = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                btnFecha.text = fechaSeleccionada
            }, year, month, day)
            dpd.show()
        }

        btnHora.setOnClickListener {
            val calendario = Calendar.getInstance()
            val hour = if (horaSeleccionada.contains(":")) horaSeleccionada.split(":")[1].toInt().let { horaSeleccionada.split(":")[0].toInt() } else calendario.get(Calendar.HOUR_OF_DAY)
            val minute = if (horaSeleccionada.contains(":")) horaSeleccionada.split(":")[1].toInt() else calendario.get(Calendar.MINUTE)
            
            val tpd = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                horaSeleccionada = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                btnHora.text = horaSeleccionada
            }, hour, minute, true)
            tpd.show()
        }

        btnGuardar.setOnClickListener {
            val nombre = etTitulo.text.toString().trim()
            val estaActivado = switchRecordatorio.isChecked
            
            if (nombre.isNotEmpty()) {
                if (tareaAEditar == null) {
                    guardarTareaReal(nombre, categoriaSeleccionada, fechaSeleccionada, horaSeleccionada, estaActivado, prioridadSeleccionada)
                } else {
                    val tareaActualizada = tareaAEditar.copy(
                        titulo = nombre,
                        categoria = categoriaSeleccionada,
                        fechaLimite = fechaSeleccionada,
                        horaLimite = horaSeleccionada,
                        recordatorioActivado = estaActivado,
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

    private fun mostrarDialogoGestionCategorias() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar Categorías")
        val categoriasNombres = listaCategorias.filter { it.nombre != "Todas" }.map { it.nombre }.toTypedArray()
        builder.setItems(categoriasNombres) { _, which ->
            val categoriaElegida = listaCategorias.filter { it.nombre != "Todas" }[which]
            mostrarDialogoEditarCategoria(categoriaElegida)
        }
        builder.setNegativeButton("Cerrar") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun mostrarDialogoEditarCategoria(categoria: Categoria) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar: ${categoria.nombre}")
        val input = EditText(this)
        input.setText(categoria.nombre)
        input.setPadding(50, 40, 50, 40)
        builder.setView(input)
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nuevoNombre = input.text.toString().trim()
            if (nuevoNombre.isNotEmpty() && nuevoNombre != categoria.nombre) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val catRef = FirebaseDatabase.getInstance().getReference("Usuarios").child(userId ?: "anonimo").child("Categorias")
                catRef.child(categoria.id).child("nombre").setValue(nuevoNombre)
                actualizarTareasConNuevaCategoria(categoria.nombre, nuevoNombre)
                Toast.makeText(this, "Categoría actualizada", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNeutralButton("Eliminar") { _, _ ->
            if (categoria.nombre == "Personal" || categoria.nombre == "Universidad") {
                Toast.makeText(this, "No se pueden eliminar las categorías base", Toast.LENGTH_SHORT).show()
            } else {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                FirebaseDatabase.getInstance().getReference("Usuarios").child(userId ?: "anonimo").child("Categorias").child(categoria.id).removeValue()
                actualizarTareasConNuevaCategoria(categoria.nombre, "Personal")
                Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun actualizarTareasConNuevaCategoria(viejoNombre: String, nuevoNombre: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val tareasRef = FirebaseDatabase.getInstance().getReference("Usuarios").child(userId ?: "anonimo").child("Tareas")
        tareasRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (tareaSnapshot in snapshot.children) {
                    val catTarea = tareaSnapshot.child("categoria").value as? String
                    if (catTarea == viejoNombre) {
                        tareaSnapshot.ref.child("categoria").setValue(nuevoNombre)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun solicitarPermisosNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permiso = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED -> {
                    // Ya tiene permiso
                }
                ActivityCompat.shouldShowRequestPermissionRationale(this, permiso) -> {
                    // El usuario lo denegó una vez, explicamos por qué es necesario
                    AlertDialog.Builder(this)
                        .setTitle("Permiso de Notificaciones")
                        .setMessage("Necesitamos este permiso para avisarte cuando tus tareas venzan.")
                        .setPositiveButton("Aceptar") { _, _ ->
                            requestPermissionLauncher.launch(permiso)
                        }
                        .setNegativeButton("No, gracias", null)
                        .show()
                }
                else -> {
                    // Pedir por primera vez
                    requestPermissionLauncher.launch(permiso)
                }
            }
        }

        // Alarmas exactas (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Alarmas Exactas")
                    .setMessage("Para que los recordatorios suenen al instante, necesitamos este permiso.")
                    .setPositiveButton("Configurar") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Omitir", null)
                    .show()
            }
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.notificacion_canal_id)
            val name = getString(R.string.notificacion_canal_nombre)
            val descriptionText = "Canal para recordatorios de tareas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
