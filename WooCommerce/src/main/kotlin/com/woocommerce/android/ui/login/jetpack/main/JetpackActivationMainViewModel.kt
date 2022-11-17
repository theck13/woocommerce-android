package com.woocommerce.android.ui.login.jetpack.main

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.ui.common.PluginRepository
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivated
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivationFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstallFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstalled
import com.woocommerce.android.ui.login.jetpack.JetpackActivationRepository
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.SiteModel
import javax.inject.Inject

@HiltViewModel
class JetpackActivationMainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jetpackActivationRepository: JetpackActivationRepository,
    private val pluginRepository: PluginRepository
) : ScopedViewModel(savedStateHandle) {
    companion object {
        private const val JETPACK_SLUG = "jetpack"
        private const val JETPACK_NAME = "jetpack/jetpack"
    }

    private val navArgs: JetpackActivationMainFragmentArgs by savedStateHandle.navArgs()
    private val site: Deferred<SiteModel>
        get() = async {
            requireNotNull(jetpackActivationRepository.getSiteByUrl(navArgs.siteUrl)) {
                "Site not cached"
            }
        }
    private var installationJob: Job? = null

    private val currentStep = savedStateHandle.getStateFlow(
        scope = viewModelScope,
        initialValue = Step(if (navArgs.isJetpackInstalled) StepType.Connection else StepType.Installation),
    )
    val viewState = combine(
        currentStep,
        flowOf(if (navArgs.isJetpackInstalled) stepsForConnection() else stepsForInstallation())
    ) { currentStep, steps ->
        ViewState(
            siteUrl = navArgs.siteUrl,
            isJetpackInstalled = navArgs.isJetpackInstalled,
            steps = steps.map { stepType ->
                Step(
                    type = stepType,
                    state = when {
                        currentStep.type == stepType -> currentStep.state
                        currentStep.type > stepType -> StepState.Success
                        else -> StepState.Idle
                    }
                )
            }
        )
    }.asLiveData()

    init {
        monitorCurrentStep()
        startNextStep()
    }

    fun onCloseClick() {
        triggerEvent(Exit)
    }

    private fun startNextStep() {
        currentStep.update { it.copy(state = StepState.Ongoing) }
    }

    private fun monitorCurrentStep() {
        currentStep
            .filter { it.state == StepState.Ongoing }
            .distinctUntilChanged { step1, step2 -> step1.type == step2.type }
            .onEach {
                WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: handle step: $it")
                when (it.type) {
                    StepType.Installation, StepType.Activation -> startJetpackInstallation()
                    StepType.Connection -> startJetpackConnection()
                    StepType.Done -> TODO()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun startJetpackInstallation() {
        if (installationJob?.isActive == true) return
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: start Jetpack Installation")
        installationJob = launch {
            pluginRepository.installPlugin(
                site = site.await(),
                slug = JETPACK_SLUG,
                name = JETPACK_NAME
            ).collect { status ->
                when (status) {
                    is PluginInstalled -> {
                        currentStep.value = Step(type = StepType.Activation, state = StepState.Ongoing)
                    }
                    is PluginInstallFailed -> {
                        currentStep.value =
                            Step(type = StepType.Installation, state = StepState.Error(status.errorCode))
                    }
                    is PluginActivated -> {
                        currentStep.value = Step(type = StepType.Connection, state = StepState.Ongoing)
                    }
                    is PluginActivationFailed -> {
                        currentStep.value = Step(type = StepType.Activation, state = StepState.Error(status.errorCode))
                    }
                }
            }
        }
    }

    private fun startJetpackConnection() {
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: start Jetpack Connection")
    }

    private fun stepsForInstallation() = StepType.values()

    private fun stepsForConnection() = arrayOf(StepType.Connection, StepType.Done)

    data class ViewState(
        val siteUrl: String,
        val isJetpackInstalled: Boolean,
        val steps: List<Step>
    )

    @Parcelize
    data class Step(
        val type: StepType,
        val state: StepState = StepState.Idle
    ) : Parcelable

    enum class StepType {
        // The declaration order is important
        Installation, Activation, Connection, Done
    }

    sealed interface StepState : Parcelable {
        @Parcelize
        object Idle : StepState

        @Parcelize
        object Ongoing : StepState

        @Parcelize
        object Success : StepState

        @Parcelize
        data class Error(val code: Int?) : StepState
    }
}
