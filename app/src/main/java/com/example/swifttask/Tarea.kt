package com.example.swifttask // Asegúrate de que este sea tu package real

data class Tarea(
    val id: String? = null,
    val titulo: String = "",
    val asignatura: String = "Personal", // Por defecto será Personal
    val fechaLimite: String = "",       // Aquí guardaremos el deadline
    val esRepetitiva: Boolean = false,
    val completada: Boolean = false
)