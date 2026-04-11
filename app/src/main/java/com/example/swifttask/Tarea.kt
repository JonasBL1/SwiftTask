package com.example.swifttask

data class Tarea(
    val id: String? = null,
    val titulo: String = "",
    val categoria: String = "Personal",
    val fechaLimite: String = "",
    val horaLimite: String = "", // Nueva propiedad para la hora
    val prioridad: String = "Media", // Alta, Media, Baja
    val esRepetitiva: Boolean = false,
    val recordatorioActivado: Boolean = false, // Nueva propiedad para saber si tiene alarma
    var completada: Boolean = false
)