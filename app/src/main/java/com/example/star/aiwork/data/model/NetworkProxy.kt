package com.example.star.aiwork.data.model

sealed class NetworkProxy {
    data object None : NetworkProxy()

    data class Http(
        val host: String,
        val port: Int
    ) : NetworkProxy()
}

