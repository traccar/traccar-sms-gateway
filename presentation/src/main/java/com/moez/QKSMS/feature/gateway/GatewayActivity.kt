package com.moez.QKSMS.feature.gateway

import android.graphics.Typeface
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.FontProvider
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.collapsing_toolbar.*
import kotlinx.android.synthetic.main.gateway_activity.*
import javax.inject.Inject


class GatewayActivity : QkThemedActivity(), GatewayView {

    @Inject lateinit var fontProvider: FontProvider
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val stateClickIntent by lazy { serviceButton.clicks() }

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[GatewayViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gateway_activity)
        setTitle(R.string.gateway_title)
        showBackButton(true)
        viewModel.bindView(this)

        if (!prefs.systemFont.get()) {
            fontProvider.getLato { lato ->
                val typeface = Typeface.create(lato, Typeface.BOLD)
                collapsingToolbar.setCollapsedTitleTypeface(typeface)
                collapsingToolbar.setExpandedTitleTypeface(typeface)
            }
        }

        colors.theme().let { theme ->
            serviceButton.setTextColor(theme.textPrimary)
            serviceButton.setBackgroundTint(theme.theme)
        }
    }

    override fun render(state: GatewayState) {
        serviceButton.setText(
            when (state.running) {
                true  -> R.string.gateway_stop
                false -> R.string.gateway_start
            }
        )
        disabledView.isVisible = !state.running
        enabledView.isVisible = state.running
    }

}
