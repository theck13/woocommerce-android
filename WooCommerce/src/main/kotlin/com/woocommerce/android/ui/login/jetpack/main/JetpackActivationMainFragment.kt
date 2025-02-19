package com.woocommerce.android.ui.login.jetpack.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.NavGraphMainDirections
import com.woocommerce.android.R
import com.woocommerce.android.extensions.handleNotice
import com.woocommerce.android.extensions.navigateBackWithNotice
import com.woocommerce.android.extensions.navigateBackWithResult
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.support.help.HelpActivity
import com.woocommerce.android.support.help.HelpActivity.Origin
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewFragment
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewViewModel.DisplayMode
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.ui.login.LoginActivity
import com.woocommerce.android.ui.login.jetpack.main.JetpackActivationMainViewModel.GoToPasswordScreen
import com.woocommerce.android.ui.login.jetpack.main.JetpackActivationMainViewModel.GoToStore
import com.woocommerce.android.ui.login.jetpack.main.JetpackActivationMainViewModel.ShowHelpScreen
import com.woocommerce.android.ui.login.jetpack.main.JetpackActivationMainViewModel.ShowJetpackConnectionWebView
import com.woocommerce.android.ui.login.jetpack.main.JetpackActivationMainViewModel.ShowWooNotInstalledScreen
import com.woocommerce.android.ui.main.AppBarStatus
import com.woocommerce.android.ui.main.MainActivity
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryFragment
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.login.LoginMode

@AndroidEntryPoint
class JetpackActivationMainFragment : BaseFragment() {
    companion object {
        const val CONNECTION_DISMISSED_RESULT = "connection-dismissed"
    }

    private val viewModel: JetpackActivationMainViewModel by viewModels()

    override val activityAppBarStatus: AppBarStatus
        get() = AppBarStatus.Hidden

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireActivity()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                WooThemeWithBackground {
                    JetpackActivationMainScreen(viewModel)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupObservers()
        setupResultHandlers()
    }

    private fun setupObservers() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ShowJetpackConnectionWebView -> showConnectionWebView(event)
                is GoToStore -> goToStore()
                is ShowWooNotInstalledScreen -> showWooNotInstalledScreen(event.siteUrl)
                is ShowHelpScreen -> openHelpActivity()
                is GoToPasswordScreen -> openPasswordScreen(event.email)
                is Exit -> findNavController().navigateUp()
            }
        }
    }

    private fun setupResultHandlers() {
        handleNotice(WPComWebViewFragment.WEBVIEW_RESULT) {
            viewModel.onJetpackConnected()
        }
        handleNotice(WPComWebViewFragment.WEBVIEW_DISMISSED) {
            navigateBackWithNotice(CONNECTION_DISMISSED_RESULT)
        }
    }

    private fun openHelpActivity() {
        startActivity(HelpActivity.createIntent(requireContext(), Origin.JETPACK_INSTALLATION, null))
    }

    private fun openPasswordScreen(email: String) {
        val intent = Intent(requireActivity(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            action = LoginActivity.LOGIN_WITH_WPCOM_EMAIL_ACTION
            putExtra(LoginActivity.EMAIL_PARAMETER, email)
            LoginMode.WOO_LOGIN_MODE.putInto(this)
        }
        startActivity(intent)
    }

    private fun goToStore() {
        (requireActivity() as? MainActivity)?.handleSitePickerResult() ?: run {
            val intent = Intent(requireActivity(), MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
    }

    private fun showWooNotInstalledScreen(siteUrl: String) {
        if (requireActivity() is MainActivity) {
            // Go back to the site picker
            navigateBackWithResult(
                key = SitePickerSiteDiscoveryFragment.SITE_PICKER_SITE_ADDRESS_RESULT,
                result = siteUrl,
                destinationId = R.id.sitePickerFragment
            )
        } else {
            // For login flow, open MainActivity after saving the site address
            AppPrefs.setLoginSiteAddress(siteUrl)
            val intent = Intent(requireActivity(), MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
    }

    private fun showConnectionWebView(event: ShowJetpackConnectionWebView) {
        findNavController().navigateSafely(
            directions = NavGraphMainDirections.actionGlobalWPComWebViewFragment(
                urlToLoad = event.url,
                urlsToTriggerExit = event.connectionValidationUrls.toTypedArray(),
                title = getString(R.string.login_jetpack_installation_approve_connection),
                displayMode = DisplayMode.MODAL
            ),
            navOptions = NavOptions.Builder()
                .setEnterAnim(R.anim.slide_up)
                .setExitAnim(R.anim.no_anime)
                .setPopEnterAnim(R.anim.no_anime)
                .setPopExitAnim(R.anim.slide_down)
                .build()
        )
    }
}
