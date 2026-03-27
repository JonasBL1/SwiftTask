package com.example.swifttask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TareaAdapter(
    private val lista: List<Tarea>,
    private val onEliminarClick: (Tarea) -> Unit,
    private val onEstadoClick: (Tarea) -> Unit
) : RecyclerView.Adapter<TareaAdapter.TareaViewHolder>() {

    class TareaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbTarea: CheckBox = view.findViewById(R.id.cbTarea)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloTarea)
        val tvAsignatura: TextView = view.findViewById(R.id.tvAsignatura)
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
        holder.tvAsignatura.text = tarea.asignatura
        holder.tvFecha.text = "Vence: ${tarea.fechaLimite}"

        // --- LÓGICA DE ESTADO VISUAL (TACHADO) ---
        if (tarea.completada) {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvTitulo.setTextColor(android.graphics.Color.GRAY)
        } else {
            holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvTitulo.setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
        }

        // --- LÓGICA DE COLORES POR MATERIA ---
        val colorEtiqueta: Int
        val colorTextoEtiqueta: Int

        when (tarea.asignatura) {
            "Matemáticas" -> {
                colorEtiqueta = android.graphics.Color.parseColor("#FAD2D2")
                colorTextoEtiqueta = android.graphics.Color.parseColor("#A50000")
            }
            "Programación" -> {
                colorEtiqueta = android.graphics.Color.parseColor("#D2FAD2")
                colorTextoEtiqueta = android.graphics.Color.parseColor("#006400")
            }
            "Física", "Química" -> {
                colorEtiqueta = android.graphics.Color.parseColor("#D2E4FA")
                colorTextoEtiqueta = android.graphics.Color.parseColor("#003366")
            }
            "Historia" -> {
                colorEtiqueta = android.graphics.Color.parseColor("#F2D2FA")
                colorTextoEtiqueta = android.graphics.Color.parseColor("#4B0082")
            }
            else -> {
                colorEtiqueta = android.graphics.Color.parseColor("#EEEEEE")
                colorTextoEtiqueta = android.graphics.Color.parseColor("#424242")
            }
        }

        holder.tvAsignatura.background.setTint(colorEtiqueta)
        holder.tvAsignatura.setTextColor(colorTextoEtiqueta)

        // Configuración del CheckBox
        holder.cbTarea.setOnCheckedChangeListener(null)
        holder.cbTarea.isChecked = tarea.completada

        holder.cbTarea.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvTitulo.setTextColor(android.graphics.Color.GRAY)
            } else {
                holder.tvTitulo.paintFlags = holder.tvTitulo.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.tvTitulo.setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
            }
            val tareaActualizada = tarea.copy(completada = isChecked)
            onEstadoClick(tareaActualizada)
        }

        holder.btnEliminar.setOnClickListener {
            onEliminarClick(tarea)
        }
    } // <--- LLAVE QUE CIERRA onBindViewHolder

    override fun getItemCount(): Int = lista.size
} // <--- LLAVE QUE CIERRA LA CLASE