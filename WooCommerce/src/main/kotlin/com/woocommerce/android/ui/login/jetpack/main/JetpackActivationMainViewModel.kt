package com.woocommerce.android.ui.login.jetpack.main

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.OnChangedException
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.extensions.isNotNullOrEmpty
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.common.PluginRepository
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivated
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivationFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstallFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstalled
import com.woocommerce.android.ui.login.AccountRepository
import com.woocommerce.android.ui.login.jetpack.JetpackActivationRepository
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.JetpackStore.JetpackConnectionUrlError
import org.wordpress.android.fluxc.store.JetpackStore.JetpackUserError
import org.wordpress.android.util.UrlUtils
import javax.inject.Inject

@HiltViewModel
class JetpackActivationMainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jetpackActivationRepository: JetpackActivationRepository,
    private val pluginRepository: PluginRepository,
    private val accountRepository: AccountRepository,
    private val selectedSite: SelectedSite,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper
) : ScopedViewModel(savedStateHandle) {
    companion object {
        private const val JETPACK_SLUG = "jetpack"
        private const val JETPACK_NAME = "jetpack/jetpack"
        private const val JETPACK_PLANS_URL = "wordpress.com/jetpack/connect/plans"
        private const val DELAY_AFTER_CONNECTION_MS = 500L
        private const val DELAY_BEFORE_SHOWING_ERROR_STATE_MS = 1000L
        private const val CONNECTED_EMAIL_KEY = "connected-email"
    }

    private val navArgs: JetpackActivationMainFragmentArgs by savedStateHandle.navArgs()
    private val site: Deferred<SiteModel>
        get() = async {
            val site = jetpackActivationRepository.getSiteByUrl(navArgs.siteUrl)?.takeIf {
                val hasCredentials = it.username.isNotNullOrEmpty() && it.password.isNotNullOrEmpty()
                if (!hasCredentials) {
                    WooLog.w(WooLog.T.LOGIN, "The found site for jetpack activation doesn't have credentials")
                }
                hasCredentials
            }
            requireNotNull(site) {
                "Site not cached"
            }
        }
    private var jetpackConnectedEmail
        get() = savedState.get<String>(CONNECTED_EMAIL_KEY).orEmpty()
        set(value) = savedState.set(CONNECTED_EMAIL_KEY, value)

    private val currentStep = savedStateHandle.getStateFlow(
        scope = viewModelScope,
        initialValue = Step(if (navArgs.isJetpackInstalled) StepType.Connection else StepType.Installation),
    )
    private val connectionStep = savedStateHandle.getStateFlow(
        scope = viewModelScope,
        initialValue = ConnectionStep.PreConnection
    )
    private var isShowingErrorState = MutableStateFlow(false)
    val viewState = combine(
        currentStep,
        connectionStep,
        flowOf(if (navArgs.isJetpackInstalled) stepsForConnection() else stepsForInstallation()),
        isShowingErrorState
    ) { currentStep, connectionStep, stepTypes, isShowingErrorState ->
        when (isShowingErrorState) {
            false -> ViewState.ProgressViewState(
                siteUrl = UrlUtils.removeScheme(navArgs.siteUrl),
                isJetpackInstalled = navArgs.isJetpackInstalled,
                steps = stepTypes.map { stepType ->
                    Step(
                        type = stepType,
                        state = when {
                            currentStep.type == stepType -> currentStep.state
                            currentStep.type > stepType -> StepState.Success
                            else -> StepState.Idle
                        }
                    )
                },
                connectionStep = connectionStep
            )

            true -> ViewState.ErrorViewState(
                stepType = currentStep.type,
                errorCode = (currentStep.state as? StepState.Error)?.code
            )
        }
    }.asLiveData()

    init {
        analyticsTrackerWrapper.track(AnalyticsEvent.LOGIN_JETPACK_SETUP_SCREEN_VIEWED)
        monitorCurrentStep()
        handleErrorStates()
        startNextStep()
    }

    fun onCloseClick() {
        analyticsTrackerWrapper.track(
            stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_SCREEN_DISMISSED,
            properties = mapOf(AnalyticsTracker.KEY_JETPACK_INSTALLATION_STEP to currentStep.value.type.analyticsName),
        )
        triggerEvent(Exit)
    }

    fun onContinueClick() = launch {
        analyticsTrackerWrapper.track(stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_GO_TO_STORE_BUTTON_TAPPED)
        val loggedInEmail = accountRepository.getUserAccount()?.email
        if (jetpackConnectedEmail == loggedInEmail) {
            val site = jetpackActivationRepository.getSiteByUrl(navArgs.siteUrl)
            requireNotNull(site) { "Illegal state, the button shouldn't be visible before fetching the site" }
            if (site.hasWooCommerce) {
                selectedSite.set(site)
                triggerEvent(GoToStore)
            } else {
                triggerEvent(ShowWooNotInstalledScreen(navArgs.siteUrl))
            }
        } else {
            // Persist the site address to allow auto-login after password verification
            appPrefsWrapper.setLoginSiteAddress(navArgs.siteUrl)
            triggerEvent(GoToPasswordScreen(jetpackConnectedEmail))
        }
    }

    fun onJetpackConnected() {
        connectionStep.value = ConnectionStep.Validation
    }

    fun onRetryClick() {
        analyticsTrackerWrapper.track(
            stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_TRY_AGAIN_BUTTON_TAPPED,
            properties = mapOf(AnalyticsTracker.KEY_JETPACK_INSTALLATION_STEP to currentStep.value.type.analyticsName)
        )
        startNextStep()
    }

    fun onGetHelpClick() {
        analyticsTrackerWrapper.track(
            stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_GET_SUPPORT_BUTTON_TAPPED,
            properties = mapOf(AnalyticsTracker.KEY_JETPACK_INSTALLATION_STEP to currentStep.value.type.analyticsName),
        )
        triggerEvent(ShowHelpScreen)
    }

    private fun startNextStep() {
        currentStep.update { it.copy(state = StepState.Ongoing) }
    }

    private fun monitorCurrentStep() {
        currentStep
            .map { step ->
                step.copy(
                    type = if (step.type == StepType.Activation) {
                        // To allow restarting the Jetpack installation after process-death, consider the Activation
                        // same as the Installation events
                        StepType.Installation
                    } else step.type
                )
            }
            .distinctUntilChanged()
            .filter { it.state == StepState.Ongoing }
            .map { it.type }
            .onEach { stepType ->
                WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: handle step: $stepType")
                when (stepType) {
                    StepType.Installation -> startJetpackInstallation()
                    StepType.Connection -> {
                        var connectionStepJob: Job? = null
                        connectionStepJob = connectionStep.onEach { connectionStep ->
                            when (connectionStep) {
                                ConnectionStep.PreConnection -> startJetpackConnection()
                                ConnectionStep.Validation -> startJetpackValidation()
                                ConnectionStep.Approved -> {
                                    currentStep.value = Step(
                                        type = StepType.Connection,
                                        state = StepState.Success
                                    )
                                    delay(DELAY_AFTER_CONNECTION_MS)
                                    currentStep.value = Step(
                                        type = StepType.Done,
                                        state = StepState.Ongoing
                                    )
                                    // Cancel collection to move to next steps
                                    connectionStepJob?.cancel()
                                }
                            }
                        }.launchIn(viewModelScope)
                    }

                    StepType.Done -> {
                        analyticsTrackerWrapper.track(stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_ALL_STEPS_MARKED_DONE)
                        currentStep.value = Step(
                            type = StepType.Done,
                            state = StepState.Success
                        )
                    }

                    StepType.Activation -> error("Type Activation is not expected here")
                }
            }
            .launchIn(viewModelScope)
    }

    private fun handleErrorStates() {
        currentStep.onEach { step ->
            when (step.state) {
                is StepState.Error -> {
                    delay(DELAY_BEFORE_SHOWING_ERROR_STATE_MS)
                    isShowingErrorState.value = true
                }
                else -> isShowingErrorState.value = false
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun startJetpackInstallation() {
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: start Jetpack Installation")
        pluginRepository.installPlugin(
            site = site.await(),
            slug = JETPACK_SLUG,
            name = JETPACK_NAME
        ).collect { status ->
            when (status) {
                is PluginInstalled -> {
                    analyticsTrackerWrapper.track(AnalyticsEvent.LOGIN_JETPACK_SETUP_INSTALL_SUCCESSFUL)
                    currentStep.value = Step(type = StepType.Activation, state = StepState.Ongoing)
                }

                is PluginInstallFailed -> {
                    analyticsTrackerWrapper.track(
                        stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_INSTALL_FAILED,
                        properties = mapOf(AnalyticsTracker.KEY_ERROR_CODE to status.errorCode.toString()),
                        errorContext = this@JetpackActivationMainViewModel::class.simpleName,
                        errorType = status.errorType,
                        errorDescription = status.errorDescription
                    )
                    currentStep.update { state -> state.copy(state = StepState.Error(status.errorCode)) }
                }

                is PluginActivated -> {
                    analyticsTrackerWrapper.track(AnalyticsEvent.LOGIN_JETPACK_SETUP_ACTIVATION_SUCCESSFUL)
                    currentStep.value = Step(type = StepType.Connection, state = StepState.Ongoing)
                }

                is PluginActivationFailed -> {
                    analyticsTrackerWrapper.track(
                        stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_ACTIVATION_FAILED,
                        properties = mapOf(AnalyticsTracker.KEY_ERROR_CODE to status.errorCode.toString()),
                        errorContext = this@JetpackActivationMainViewModel::class.simpleName,
                        errorType = status.errorType,
                        errorDescription = status.errorDescription
                    )
                    currentStep.update { state -> state.copy(state = StepState.Error(status.errorCode)) }
                }
            }
        }
    }

    private suspend fun startJetpackConnection() {
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: start Jetpack Connection")
        jetpackActivationRepository.fetchJetpackConnectionUrl(site.await()).fold(
            onSuccess = { connectionUrl ->
                analyticsTrackerWrapper.track(
                    stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_FETCH_JETPACK_CONNECTION_URL_SUCCESSFUL
                )
                triggerEvent(
                    ShowJetpackConnectionWebView(
                        url = connectionUrl,
                        connectionValidationUrls = listOf(JETPACK_PLANS_URL, navArgs.siteUrl)
                    )
                )
            },
            onFailure = {
                val error = (it as? OnChangedException)?.error as? JetpackConnectionUrlError
                analyticsTrackerWrapper.track(
                    stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_ACTIVATION_FAILED,
                    properties = mapOf(AnalyticsTracker.KEY_ERROR_CODE to error?.errorCode.toString()),
                    errorContext = this@JetpackActivationMainViewModel::class.simpleName,
                    errorType = it::class.simpleName,
                    errorDescription = it.message.orEmpty()
                )
                currentStep.update { state -> state.copy(state = StepState.Error(error?.errorCode)) }
            }
        )
    }

    private suspend fun startJetpackValidation() {
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: start Jetpack Connection validation")
        jetpackActivationRepository.fetchJetpackConnectedEmail(site.await()).fold(
            onSuccess = { email ->
                jetpackConnectedEmail = email
                if (accountRepository.getUserAccount()?.email != email) {
                    analyticsTrackerWrapper.track(
                        stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_AUTHORIZED_USING_DIFFERENT_WPCOM_ACCOUNT
                    )
                    WooLog.d(
                        WooLog.T.LOGIN,
                        "Jetpack Activation: connection made using a different email than the logged in one"
                    )
                    connectionStep.value = ConnectionStep.Approved
                } else {
                    confirmSiteConnection()
                }
            },
            onFailure = {
                val error = (it as? OnChangedException)?.error as? JetpackUserError
                analyticsTrackerWrapper.track(
                    stat = AnalyticsEvent.LOGIN_JETPACK_SETUP_ERROR_CHECKING_JETPACK_CONNECTION,
                    properties = mapOf(AnalyticsTracker.KEY_ERROR_CODE to error?.errorCode.toString()),
                    errorContext = this@JetpackActivationMainViewModel::class.simpleName,
                    errorType = it::class.simpleName,
                    errorDescription = it.message.orEmpty()
                )
                currentStep.update { state -> state.copy(state = StepState.Error(error?.errorCode)) }
            }
        )
    }

    private suspend fun confirmSiteConnection() {
        WooLog.d(WooLog.T.LOGIN, "Jetpack Activation: fetch sites and confirm site connection")
        jetpackActivationRepository.checkSiteConnection(navArgs.siteUrl).fold(
            onSuccess = {
                connectionStep.value = ConnectionStep.Approved
            },
            onFailure = {
                analyticsTrackerWrapper.track(
                    stat = AnalyticsEvent.LOGIN_JETPACK_FETCHING_WPCOM_SITES_FAILED,
                    errorContext = this@JetpackActivationMainViewModel::class.simpleName,
                    errorType = it::class.simpleName,
                    errorDescription = it.message.orEmpty()
                )
                currentStep.update { state -> state.copy(state = StepState.Error(null)) }
            }
        )
    }

    private fun stepsForInstallation() = StepType.values()

    private fun stepsForConnection() = arrayOf(StepType.Connection, StepType.Done)

    sealed interface ViewState {
        data class ProgressViewState(
            val siteUrl: String,
            val isJetpackInstalled: Boolean,
            val steps: List<Step>,
            val connectionStep: ConnectionStep
        ) : ViewState {
            val isDone = steps.all { it.state == StepState.Success }
        }

        data class ErrorViewState(
            val stepType: StepType,
            val errorCode: Int?
        ) : ViewState
    }

    @Parcelize
    data class Step(
        val type: StepType,
        val state: StepState = StepState.Idle
    ) : Parcelable

    enum class StepType(val analyticsName: String) {
        // The declaration order is important
        Installation("installation"),
        Activation("activation"),
        Connection("connection"),
        Done("all_done")
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

    enum class ConnectionStep {
        PreConnection, Validation, Approved
    }

    data class ShowJetpackConnectionWebView(
        val url: String,
        val connectionValidationUrls: List<String>
    ) : MultiLiveEvent.Event()

    object GoToStore : MultiLiveEvent.Event()
    data class GoToPasswordScreen(val email: String) : MultiLiveEvent.Event()
    data class ShowWooNotInstalledScreen(val siteUrl: String) : MultiLiveEvent.Event()
    object ShowHelpScreen : MultiLiveEvent.Event()
}
