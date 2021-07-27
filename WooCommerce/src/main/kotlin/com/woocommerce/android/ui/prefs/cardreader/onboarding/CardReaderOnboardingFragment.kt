package com.woocommerce.android.ui.prefs.cardreader.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import com.woocommerce.android.AppUrls
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentCardReaderOnboardingBinding
import com.woocommerce.android.databinding.FragmentCardReaderOnboardingLoadingBinding
import com.woocommerce.android.databinding.FragmentCardReaderOnboardingUnsupportedCountryBinding
import com.woocommerce.android.databinding.FragmentCardReaderOnboardingWcpayBinding
import com.woocommerce.android.extensions.exhaustive
import com.woocommerce.android.extensions.navigateBackWithNotice
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.util.UiHelpers
import com.woocommerce.android.viewmodel.MultiLiveEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CardReaderOnboardingFragment : BaseFragment(R.layout.fragment_card_reader_onboarding) {
    val viewModel: CardReaderOnboardingViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCardReaderOnboardingBinding.bind(view)
        initObservers(binding)
    }

    private fun initObservers(binding: FragmentCardReaderOnboardingBinding) {
        viewModel.event.observe(
            viewLifecycleOwner,
            { event ->
                when (event) {
                    is CardReaderOnboardingViewModel.OnboardingEvent.NavigateToSupport -> {
                        // todo cardreader start HelpActivity
                    }
                    is CardReaderOnboardingViewModel.OnboardingEvent.ViewLearnMore -> {
                        ChromeCustomTabUtils.launchUrl(requireActivity(), AppUrls.WOOCOMMERCE_LEARN_MORE_ABOUT_PAYMENTS)
                    }
                    is MultiLiveEvent.Event.Exit -> navigateBackWithNotice(KEY_READER_ONBOARDING_RESULT)
                    else -> event.isHandled = false
                }
            }
        )

        viewModel.viewStateData.observe(
            viewLifecycleOwner,
            { state ->
                showOnboardingLayout(binding, state)
            }
        )
    }

    private fun showOnboardingLayout(
        binding: FragmentCardReaderOnboardingBinding,
        state: CardReaderOnboardingViewModel.OnboardingViewState
    ) {
        binding.container.removeAllViews()
        val layout = LayoutInflater.from(requireActivity()).inflate(state.layoutRes, binding.container, false)
        binding.container.addView(layout)
        when (state) {
            is CardReaderOnboardingViewModel.OnboardingViewState.GenericErrorState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.LoadingState ->
                showLoadingState(layout, state)
            is CardReaderOnboardingViewModel.OnboardingViewState.NoConnectionErrorState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.UnsupportedCountryState ->
                showCountryNotSupportedState(layout, state)
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayAccountOverdueRequirementsState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayAccountPendingRequirementsState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayAccountRejectedState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayAccountUnderReviewState -> TODO()
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayInTestModeWithLiveAccountState -> TODO()

            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotActivatedState ->
                showWCPayNotActivatedState(layout, state)
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotInstalledState ->
                showWCPayNotInstalledState(layout, state)
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotSetupState ->
                showWCPayNotSetupState(layout, state)
            is CardReaderOnboardingViewModel.OnboardingViewState.WCPayUnsupportedVersionState ->
                showWCPayUnsupportedVersionState(layout, state)
        }.exhaustive
    }

    private fun showLoadingState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.LoadingState
    ) {
        val binding = FragmentCardReaderOnboardingLoadingBinding.bind(view)
        binding.cancelButton.setOnClickListener {
            viewModel.onCancelClicked()
        }
    }

    private fun showWCPayNotActivatedState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotActivatedState
    ) {
        val binding = FragmentCardReaderOnboardingWcpayBinding.bind(view)
        UiHelpers.setTextOrHide(binding.textHeader, state.headerLabel)
        UiHelpers.setTextOrHide(binding.textLabel, state.hintLabel)
        UiHelpers.setTextOrHide(binding.refreshButton, state.refreshButtonLabel)
        UiHelpers.setTextOrHide(binding.cardReaderDetailLearnMoreTv.learnMore, state.learnMoreLabel)
        UiHelpers.setImageOrHide(binding.illustration, state.illustration)
        binding.refreshButton.setOnClickListener {
            state.refreshButtonAction.invoke()
        }
        binding.cardReaderDetailLearnMoreTv.learnMore.setOnClickListener {
            state.onLearnMoreActionClicked.invoke()
        }
    }

    private fun showWCPayNotInstalledState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotInstalledState
    ) {
        val binding = FragmentCardReaderOnboardingWcpayBinding.bind(view)
        UiHelpers.setTextOrHide(binding.textHeader, state.headerLabel)
        UiHelpers.setTextOrHide(binding.textLabel, state.hintLabel)
        UiHelpers.setTextOrHide(binding.refreshButton, state.refreshButtonLabel)
        UiHelpers.setTextOrHide(binding.cardReaderDetailLearnMoreTv.learnMore, state.learnMoreLabel)
        UiHelpers.setImageOrHide(binding.illustration, state.illustration)
        binding.refreshButton.setOnClickListener {
            state.refreshButtonAction.invoke()
        }
        binding.cardReaderDetailLearnMoreTv.learnMore.setOnClickListener {
            state.onLearnMoreActionClicked.invoke()
        }
    }

    private fun showWCPayUnsupportedVersionState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.WCPayUnsupportedVersionState
    ) {
        val binding = FragmentCardReaderOnboardingWcpayBinding.bind(view)
        UiHelpers.setTextOrHide(binding.textHeader, state.headerLabel)
        UiHelpers.setTextOrHide(binding.textLabel, state.hintLabel)
        UiHelpers.setTextOrHide(binding.refreshButton, state.refreshButtonLabel)
        UiHelpers.setTextOrHide(binding.cardReaderDetailLearnMoreTv.learnMore, state.learnMoreLabel)
        UiHelpers.setImageOrHide(binding.illustration, state.illustration)
        binding.refreshButton.setOnClickListener {
            state.refreshButtonAction.invoke()
        }
        binding.cardReaderDetailLearnMoreTv.learnMore.setOnClickListener {
            state.onLearnMoreActionClicked.invoke()
        }
    }

    private fun showWCPayNotSetupState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.WCPayNotSetupState
    ) {
        val binding = FragmentCardReaderOnboardingWcpayBinding.bind(view)
        UiHelpers.setTextOrHide(binding.textHeader, state.headerLabel)
        UiHelpers.setTextOrHide(binding.textLabel, state.hintLabel)
        UiHelpers.setTextOrHide(binding.refreshButton, state.refreshButtonLabel)
        UiHelpers.setTextOrHide(binding.cardReaderDetailLearnMoreTv.learnMore, state.learnMoreLabel)
        UiHelpers.setImageOrHide(binding.illustration, state.illustration)
        binding.refreshButton.setOnClickListener {
            state.refreshButtonAction.invoke()
        }
        binding.cardReaderDetailLearnMoreTv.learnMore.setOnClickListener {
            state.onLearnMoreActionClicked.invoke()
        }
    }

    private fun showCountryNotSupportedState(
        view: View,
        state: CardReaderOnboardingViewModel.OnboardingViewState.UnsupportedCountryState
    ) {
        val binding = FragmentCardReaderOnboardingUnsupportedCountryBinding.bind(view)
        UiHelpers.setTextOrHide(binding.unsupportedCountryHeader, state.headerLabel)
        UiHelpers.setImageOrHide(binding.unsupportedCountryIllustration, state.illustration)
        UiHelpers.setTextOrHide(binding.unsupportedCountryHint, state.hintLabel)
        UiHelpers.setTextOrHide(binding.unsupportedCountryHelp, state.contactSupportLabel)
        UiHelpers.setTextOrHide(binding.unsupportedCountryLearnMoreContainer.learnMore, state.learnMoreLabel)
        binding.unsupportedCountryHelp.setOnClickListener {
            state.onContactSupportActionClicked?.invoke()
        }
        binding.unsupportedCountryLearnMoreContainer.learnMore.setOnClickListener {
            state.onLearnMoreActionClicked?.invoke()
        }
    }

    override fun getFragmentTitle() = resources.getString(R.string.card_reader_onboarding_title)

    companion object {
        const val KEY_READER_ONBOARDING_RESULT = "key_reader_onboarding_result"
    }
}
