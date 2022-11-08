package com.woocommerce.android.ui.prefs

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentDeveloperOptionsBinding
import com.woocommerce.android.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.util.ToastUtils

@AndroidEntryPoint
class DeveloperOptionsFragment : BaseFragment(R.layout.fragment_developer_options) {
    val viewModel: DeveloperOptionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentDeveloperOptionsBinding.bind(view)

        initViews(binding)
        observeViewState(binding)
        observeEvents()
    }

    private fun initViews(binding: FragmentDeveloperOptionsBinding) {
        binding.developerOptionsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.developerOptionsRv.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.developerOptionsRv.adapter = DeveloperOptionsAdapter()
    }

    private fun observeEvents() {
        viewModel.event.observe(
            viewLifecycleOwner
        ) { event ->
            when (event) {
                is DeveloperOptionsViewModel.DeveloperOptionsEvents.ShowToastString -> {
                    ToastUtils.showToast(context, event.message)
                }
                is DeveloperOptionsViewModel.DeveloperOptionsEvents.ShowDialog -> {
                    showUpdateOptionsDialog(
                        arrayOf(
                            DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions.ALWAYS,
                            DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions.NEVER,
                            DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions.RANDOMLY
                        )
                    )
                }
            }
        }
    }

    private fun showUpdateOptionsDialog(
        values: Array<DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions>,
//        onSelected: (DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions) -> Unit,
        mapper: (DeveloperOptionsViewModel.DeveloperOptionsViewState.UpdateOptions) -> String = {it.toString()}
    ) {
        val textValues = values.map(mapper).toTypedArray()
        MaterialAlertDialogBuilder(
            ContextThemeWrapper(
                context,
                R.style.Theme_Woo_DayNight
            )
        )
            .setTitle("Update Simulated Reader")
            .setSingleChoiceItems(textValues, 0) { dialog, which ->
//                dialog.dismiss()
//                onSelected(values[which])
            }.show()
    }



    private fun observeViewState(binding: FragmentDeveloperOptionsBinding) {
        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            (binding.developerOptionsRv.adapter as DeveloperOptionsAdapter).setItems(state.rows)
        }
    }

    override fun getFragmentTitle() = resources.getString(R.string.dev_options)
}
