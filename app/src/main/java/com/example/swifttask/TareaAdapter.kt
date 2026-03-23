package com.example.swifttask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TareaAdapter(
    private val listaTareas: MutableList<Tarea>,
    private val onEliminarClick: (Tarea) -> Unit,
    private val onEstadoClick: (Tarea) -> Unit
) : RecyclerView.Adapter<TareaAdapter.TareaViewHolder>() {

    // Esta clase "sujeta" los elementos visuales de cada fila
    class TareaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTarea: TextView = view.findViewById(R.id.tvTareaTitulo)
        val cbTarea: CheckBox = view.findViewById(R.id.cbTarea)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TareaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tarea, parent, false)
        return TareaViewHolder(view)
    }

    override fun onBindViewHolder(holder: TareaViewHolder, position: Int) {
        // 1. PRIMERO obtenemos la tarea de la lista usando la posición
        val tarea = listaTareas[position]

        // 2. AHORA ya podemos usar la variable 'tarea'
        holder.tvTarea.text = tarea.titulo
        holder.cbTarea.isChecked = tarea.completada

        // Lógica del tachado
        if (tarea.completada) {
            holder.tvTarea.paintFlags = holder.tvTarea.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.tvTarea.paintFlags = holder.tvTarea.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Acción al borrar
        holder.btnEliminar.setOnClickListener { onEliminarClick(tarea) }

        // Acción al marcar/desmarcar
        holder.cbTarea.setOnClickListener {
            val tareaActualizada = tarea.copy(completada = holder.cbTarea.isChecked)
            onEstadoClick(tareaActualizada)
        }
    }

    override fun getItemCount(): Int = listaTareas.size
}