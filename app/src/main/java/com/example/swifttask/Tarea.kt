package com.example.swifttask

data class Tarea(
    val id: String? = null,
    val titulo: String = "",
    val asignatura: String = "Personal",
    val fechaLimite: String = "",
    val prioridad: String = "Media", // Alta, Media, Baja
    val esRepetitiva: Boolean = false,
    var completada: Boolean = false // Cambiado a var para permitir el swipe de completar
)