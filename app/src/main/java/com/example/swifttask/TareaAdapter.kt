package com.example.swifttask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TareaAdapter(
    private var lista: List<Tarea>,
    private val onEliminarClick: (Tarea) -> Unit,
    private val onEstadoClick: (Tarea) -> Unit,
    private val onTareaClick: (Tarea) -> Unit
) : RecyclerView.Adapter<TareaAdapter.TareaViewHolder>() {

    fun updateLista(nuevaLista: List<Tarea>) {
        val diffResult = DiffUtil.calculateDiff(TareaDiffCallback(lista, nuevaLista))
        lista = nuevaLista.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    class TareaDiffCallback(
        private val oldList: List<Tarea>,
        private val newList: List<Tarea>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    class TareaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbTarea: CheckBox = view.findViewById(R.id.cbTarea)
        val viewPrioridad: View = view.findViewById(R.id.viewPrioridad)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloTarea)
        val tvCategoria: TextView = view.findViewById(R.id.tvCategoria)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TareaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tarea, parent, false)
        return TareaViewHolder(view)
    }

    override fun onBindViewHolder(holder: TareaViewHolder, position: Int) {
        val tarea = lista[position]

        holder.tvTitulo.text = tarea.titulo
        holder.tvCategoria.text = tarea.categoria
        
        // --- LÓGICA DE FECHAS INTELIGENTES ---
        if (tarea.fechaLimite != holder.itemView.context.getString(R.string.sin_fecha)) {
            try {
                val sdf = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
                val fechaTarea = sdf.parse(tarea.fechaLimite)
                
                if (fechaTarea != null) {
                    val hoy = Calendar.getInstance()
                    val calTarea = Calendar.getInstance().apply { time = fechaTarea }
                    
                    // Resetear horas para comparar solo fechas
                    val resetCalendar = { cal: Calendar ->
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                    }
                    resetCalendar(hoy)
                    resetCalendar(calTarea)

                    when {
                        calTarea.before(hoy) -> {
                            holder.tvFecha.text = holder.itemView.context.getString(R.string.atrasada)
                            holder.tvFecha.setTextColor(android.graphics.Color.parseColor("#EF4444")) // Rojo
                        }
                        calTarea.timeInMillis == hoy.timeInMillis -> {
                            holder.tvFecha.text = holder.itemView.context.getString(R.string.vence_hoy)
                            holder.tvFecha.setTextColor(android.graphics.Color.parseColor("#3B82F6")) // Azul
                        }
                        calTarea.timeInMillis == hoy.timeInMillis + (24 * 60 * 60 * 1000) -> {
                            holder.tvFecha.text = holder.itemView.context.getString(R.string.vence_manana)
                            holder.tvFecha.setTextColor(android.graphics.Color.parseColor("#F59E0B")) // Naranja
                        }
                        else -> {
                            holder.tvFecha.text = holder.itemView.context.getString(R.string.vence_el, tarea.fechaLimite)
                            holder.tvFecha.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                        }
                    }
                }
            } catch (e: Exception) {
                holder.tvFecha.text = "Vence: ${tarea.fechaLimite}"
            }
        } else {
            holder.tvFecha.text = tarea.fechaLimite
            holder.tvFecha.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
        }

        // --- LÓGICA DE COLORES POR PRIORIDAD ---
        val colorPrioridad = when (tarea.prioridad) {
            "Alta" -> android.graphics.Color.parseColor("#EF4444") // Rojo
            "Media" -> android.graphics.Color.parseColor("#F59E0B") // Naranja
            "Baja" -> android.graphics.Color.parseColor("#10B981") // Verde
            else -> android.graphics.Color.parseColor("#64748B") // Gris
        }
        holder.viewPrioridad.setBackgroundColor(colorPrioridad)

        // --- LÓGICA DE ESTADO VISUAL (TACHADO) ---
        if (tarea.completada) {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvTitulo.setTextColor(android.graphics.Color.GRAY)
        } else {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvTitulo.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
        }

        // --- LÓGICA DE COLORES POR MATERIA ---
        val colorEtiqueta: Int
        val colorTextoEtiqueta: Int

        val isDarkMode = (holder.itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        when (tarea.categoria) {
            "Matemáticas" -> {
                colorEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#4A1D1D" else "#FAD2D2")
                colorTextoEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#FCA5A5" else "#A50000")
            }
            "Programación" -> {
                colorEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#143D14" else "#D2FAD2")
                colorTextoEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#86EFAC" else "#006400")
            }
            "Física", "Química" -> {
                colorEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#172554" else "#D2E4FA")
                colorTextoEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#93C5FD" else "#003366")
            }
            "Historia" -> {
                colorEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#3B0764" else "#F2D2FA")
                colorTextoEtiqueta = android.graphics.Color.parseColor(if (isDarkMode) "#D8B4FE" else "#4B0082")
            }
            else -> {
                colorEtiqueta = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.input_background)
                colorTextoEtiqueta = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
            }
        }

        holder.tvCategoria.background?.mutate()?.setTint(colorEtiqueta)
        holder.tvCategoria.setTextColor(colorTextoEtiqueta)

        // Configuración del CheckBox
        holder.cbTarea.setOnCheckedChangeListener(null)
        holder.cbTarea.isChecked = tarea.completada

        holder.cbTarea.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvTitulo.setTextColor(android.graphics.Color.GRAY)
            } else {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.tvTitulo.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
            }
            val tareaActualizada = tarea.copy(completada = isChecked)
            onEstadoClick(tareaActualizada)
        }

        holder.btnEliminar.setOnClickListener {
            onEliminarClick(tarea)
        }

        holder.itemView.setOnClickListener {
            onTareaClick(tarea)
        }
    } // <--- LLAVE QUE CIERRA onBindViewHolder

    override fun getItemCount(): Int = lista.size
} // <--- LLAVE QUE CIERRA LA CLASE