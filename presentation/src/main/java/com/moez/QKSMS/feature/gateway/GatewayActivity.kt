package com.moez.QKSMS.feature.gateway

import android.graphics.Typeface
import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.google.firebase.messaging.FirebaseMessaging
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.FontProvider
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.viewBinding
import com.moez.QKSMS.databinding.GatewayActivityBinding
import dagger.android.AndroidInjection
import javax.inject.Inject


class GatewayActivity : QkThemedActivity(), GatewayView {

    @Inject lateinit var fontProvider: FontProvider
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val tokenClickIntent by lazy { binding.tokenView.clicks().map { binding.tokenView.text.toString() } }
    override val stateClickIntent by lazy { binding.serviceButton.clicks() }

    private val binding by viewBinding(GatewayActivityBinding::inflate)
    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[GatewayViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setTitle(R.string.gateway_title)
        showBackButton(true)
        viewModel.bindView(this)

        binding.pushDescriptionView.movementMethod = LinkMovementMethod.getInstance()
        binding.tokenView.clipToOutline = true

        if (!prefs.systemFont.get()) {
            fontProvider.getLato { lato ->
                val typeface = Typeface.create(lato, Typeface.BOLD)
                binding.appBarLayout.collapsingToolbar.setCollapsedTitleTypeface(typeface)
                binding.appBarLayout.collapsingToolbar.setExpandedTitleTypeface(typeface)
            }
        }

        colors.theme().let { theme ->
            binding.serviceButton.setTextColor(theme.textPrimary)
            binding.serviceButton.setBackgroundTint(theme.theme)
        }
    }

    override fun render(state: GatewayState) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            binding.tokenView.text = task.result
        }

        binding.keyView.text = state.key

        binding.serviceButton.setText(
            when (state.running) {
                true  -> R.string.gateway_stop
                false -> R.string.gateway_start
            }
        )
        binding.disabledView.isVisible = !state.running
        binding.enabledView.isVisible = state.running

        binding.urlsView.text = state.urls.joinToString("\n\n")
    }

}
