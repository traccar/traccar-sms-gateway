package com.moez.QKSMS.feature.gateway

import androidx.lifecycle.ViewModel
import com.moez.QKSMS.injection.ViewModelKey
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

@Module
class GatewayActivityModule {

    @Provides
    @IntoMap
    @ViewModelKey(GatewayViewModel::class)
    fun provideGatewayViewModel(viewModel: GatewayViewModel): ViewModel = viewModel

}
