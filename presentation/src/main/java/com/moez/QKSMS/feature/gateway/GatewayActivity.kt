package com.moez.QKSMS.feature.gateway

import android.graphics.Typeface
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.QkDialog
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.FontProvider
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import dagger.android.AndroidInjection
import io.reactivex.Observable
import kotlinx.android.synthetic.main.collapsing_toolbar.*
import kotlinx.android.synthetic.main.gateway_activity.*
import javax.inject.Inject


class GatewayActivity : QkThemedActivity(), GatewayView {

    @Inject lateinit var fontProvider: FontProvider
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val stateClickIntent by lazy { Observable.just(Unit) }

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
            sampleMessage.setBackgroundTint(theme.theme)
            sampleMessage.setTextColor(theme.textPrimary)
            compose.setTint(theme.textPrimary)
            compose.setBackgroundTint(theme.theme)
            upgrade.setBackgroundTint(theme.theme)
            upgradeIcon.setTint(theme.textPrimary)
            upgradeLabel.setTextColor(theme.textPrimary)
        }
    }

    override fun render(state: GatewayState) {
        /*TODO()*/
    }

}
