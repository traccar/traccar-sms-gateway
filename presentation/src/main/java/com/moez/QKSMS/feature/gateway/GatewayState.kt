package com.moez.QKSMS.feature.gateway

import com.moez.QKSMS.model.ScheduledMessage

data class GatewayState(
    val addresses: List<ScheduledMessage> = listOf(),
    val running: Boolean = false
)
