package com.example.swifttask

data class Tarea(
    val id: String? = null,        // El ID único que le dará Firebase
    val titulo: String? = null,    // El texto de la tarea
    val completada: Boolean = false // Si está marcada o no
)