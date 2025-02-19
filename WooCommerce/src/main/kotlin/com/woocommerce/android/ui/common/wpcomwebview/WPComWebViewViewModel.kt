package com.woocommerce.android.ui.common.wpcomwebview

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewViewModel.UrlComparisonMode.EQUALITY
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewViewModel.UrlComparisonMode.PARTIAL
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import org.wordpress.android.fluxc.network.UserAgent
import javax.inject.Inject

@HiltViewModel
class WPComWebViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val wpComWebViewAuthenticator: WPComWebViewAuthenticator,
    val userAgent: UserAgent
) : ScopedViewModel(savedStateHandle) {
    private val navArgs: WPComWebViewFragmentArgs by savedStateHandle.navArgs()

    val viewState = navArgs.let {
        ViewState(
            urlToLoad = it.urlToLoad,
            title = it.title,
            displayMode = it.displayMode,
            captureBackButton = it.captureBackButton
        )
    }

    fun onUrlLoaded(url: String) {
        fun String.matchesUrl(url: String) = when (navArgs.urlComparisonMode) {
            PARTIAL -> url.contains(this, ignoreCase = true)
            EQUALITY -> equals(url, ignoreCase = true)
        }

        if (navArgs.urlsToTriggerExit?.any { it.matchesUrl(url) } == true) {
            triggerEvent(ExitWithResult(Unit))
        }
    }

    fun onClose() {
        triggerEvent(Exit)
    }

    data class ViewState(
        val urlToLoad: String,
        val title: String?,
        val displayMode: DisplayMode,
        val captureBackButton: Boolean
    )

    enum class UrlComparisonMode {
        PARTIAL, EQUALITY
    }

    enum class DisplayMode {
        REGULAR, MODAL
    }
}
