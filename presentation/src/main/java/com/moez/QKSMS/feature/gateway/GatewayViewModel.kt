package com.moez.QKSMS.feature.gateway

import android.content.Context
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkViewModel
import com.moez.QKSMS.common.util.BillingManager
import com.moez.QKSMS.common.util.ClipboardUtils
import com.moez.QKSMS.common.util.extensions.makeToast
import com.moez.QKSMS.interactor.SendScheduledMessage
import com.moez.QKSMS.repository.MessageRepository
import com.moez.QKSMS.repository.ScheduledMessageRepository
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import javax.inject.Inject

class GatewayViewModel @Inject constructor(
    private val context: Context
) : QkViewModel<GatewayView, GatewayState>(GatewayState()) {

    override fun bindView(view: GatewayView) {
        super.bindView(view)

        view.stateClickIntent
                .autoDisposable(view.scope())
                .subscribe { /*TODO()*/ }
    }

}
