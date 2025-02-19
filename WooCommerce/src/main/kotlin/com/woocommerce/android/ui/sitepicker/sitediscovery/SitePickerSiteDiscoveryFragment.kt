package com.woocommerce.android.ui.sitepicker.sitediscovery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.NavGraphMainDirections
import com.woocommerce.android.R
import com.woocommerce.android.extensions.handleNotice
import com.woocommerce.android.extensions.navigateBackWithResult
import com.woocommerce.android.support.ZendeskHelper
import com.woocommerce.android.support.help.HelpActivity
import com.woocommerce.android.support.help.HelpActivity.Origin
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewFragment
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewViewModel
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.ui.login.LoginActivity
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorFragment
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorViewModel.AccountMismatchErrorType
import com.woocommerce.android.ui.login.accountmismatch.AccountMismatchErrorViewModel.AccountMismatchPrimaryButton
import com.woocommerce.android.ui.main.AppBarStatus
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryViewModel.CreateZendeskTicket
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryViewModel.NavigateToHelpScreen
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryViewModel.ShowJetpackConnectionError
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryViewModel.StartNativeJetpackActivation
import com.woocommerce.android.ui.sitepicker.sitediscovery.SitePickerSiteDiscoveryViewModel.StartWebBasedJetpackInstallation
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Logout
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.login.LoginMode
import javax.inject.Inject

@AndroidEntryPoint
class SitePickerSiteDiscoveryFragment : BaseFragment() {
    companion object {
        const val SITE_PICKER_SITE_ADDRESS_RESULT = "site-url"
        private const val JETPACK_CONNECT_URL = "https://wordpress.com/jetpack/connect"
        private const val JETPACK_CONNECTED_REDIRECT_URL = "woocommerce://jetpack-connected"
    }

    private val viewModel: SitePickerSiteDiscoveryViewModel by viewModels()

    @Inject
    lateinit var zendeskHelper: ZendeskHelper

    override val activityAppBarStatus: AppBarStatus
        get() = AppBarStatus.Hidden

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireActivity()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WooThemeWithBackground {
                    SitePickerSiteDiscoveryScreen(viewModel)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupResultHandlers()
    }

    private fun setupObservers() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                CreateZendeskTicket -> {
                    zendeskHelper.createNewTicket(requireActivity(), Origin.LOGIN_SITE_ADDRESS, null)
                }
                is NavigateToHelpScreen -> {
                    startActivity(HelpActivity.createIntent(requireContext(), Origin.LOGIN_SITE_ADDRESS, null))
                }
                is StartWebBasedJetpackInstallation -> startWebBasedJetpackInstallation(event.siteAddress)
                is StartNativeJetpackActivation -> startNativeJetpackActivation(event)
                is ShowJetpackConnectionError -> showJetpackErrorScreen(event.siteAddress)
                is Logout -> onLogout()
                is ExitWithResult<*> -> navigateBackWithResult(SITE_PICKER_SITE_ADDRESS_RESULT, event.data)
                is Exit -> findNavController().navigateUp()
            }
        }
    }

    private fun setupResultHandlers() {
        handleNotice(WPComWebViewFragment.WEBVIEW_RESULT) {
            viewModel.onJetpackInstalled()
        }
        handleNotice(AccountMismatchErrorFragment.JETPACK_CONNECTED_NOTICE) {
            viewModel.onJetpackConnected()
        }
    }

    private fun startWebBasedJetpackInstallation(siteAddress: String) {
        val url = "$JETPACK_CONNECT_URL?" +
            "url=$siteAddress" +
            "&mobile_redirect=$JETPACK_CONNECTED_REDIRECT_URL" +
            "&from=mobile"

        findNavController().navigate(
            NavGraphMainDirections.actionGlobalWPComWebViewFragment(
                urlToLoad = url,
                urlsToTriggerExit = arrayOf(JETPACK_CONNECTED_REDIRECT_URL),
                urlComparisonMode = WPComWebViewViewModel.UrlComparisonMode.EQUALITY,
                title = getString(R.string.login_jetpack_install)
            )
        )
    }

    private fun startNativeJetpackActivation(event: StartNativeJetpackActivation) {
        findNavController().navigate(
            SitePickerSiteDiscoveryFragmentDirections
                .actionSitePickerSiteDiscoveryFragmentToJetpackActivation(
                    siteUrl = event.siteAddress,
                    isJetpackInstalled = event.isJetpackInstalled
                )
        )
    }

    private fun showJetpackErrorScreen(siteAddress: String) {
        findNavController().navigate(
            SitePickerSiteDiscoveryFragmentDirections
                .actionSitePickerSiteDiscoveryFragmentToAccountMismatchErrorFragment(
                    siteUrl = siteAddress,
                    allowBackNavigation = true,
                    primaryButton = AccountMismatchPrimaryButton.CONNECT_JETPACK,
                    errorType = AccountMismatchErrorType.ACCOUNT_NOT_CONNECTED
                )
        )
    }

    private fun onLogout() {
        requireActivity().setResult(Activity.RESULT_CANCELED)
        val intent = Intent(context, LoginActivity::class.java)
        LoginMode.WOO_LOGIN_MODE.putInto(intent)
        startActivity(intent)
        requireActivity().finish()
    }
}
