package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, SUCCESS, WARNING, SECURITY, ERROR
}

data class AuditLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String = ""
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
