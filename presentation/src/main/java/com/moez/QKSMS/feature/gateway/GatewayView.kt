package com.moez.QKSMS.feature.gateway

import com.moez.QKSMS.common.base.QkView
import io.reactivex.Observable

interface GatewayView : QkView<GatewayState> {

    val stateClickIntent: Observable<*>

}
