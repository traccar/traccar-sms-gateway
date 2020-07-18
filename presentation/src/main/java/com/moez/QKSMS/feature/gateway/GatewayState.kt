package com.moez.QKSMS.feature.gateway

data class GatewayState(
    val addresses: List<String> = listOf(),
    val running: Boolean = false
)
