package com.nonmessenger.models

data class ServerStatus(
    val url: String,
    val isConnected: Boolean,
    val lastPing: Long,
    val responseTime: Long,
    val messagePoolSize: Int = 0,
    val activeSessions: Int = 0
)
