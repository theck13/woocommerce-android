package com.woocommerce.android.ui.orders.filters

import android.os.Bundle
import android.view.View
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentOrderFilterListBinding
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.orders.filters.OrderFilterListViewModel.FilterListCategoryUiModel
import com.woocommerce.android.ui.orders.filters.OrderFilterListViewModel.OrderFilterListEvent.ShowOrderStatusFilterOptions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OrderFilterCategoryListFragment : BaseFragment(R.layout.fragment_order_filter_list) {

    private val viewModel: OrderFilterListViewModel by hiltNavGraphViewModels(R.id.nav_graph_order_filters)

    lateinit var orderFilterCategoryAdapter: OrderFilterCategoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)
        val binding = FragmentOrderFilterListBinding.bind(view)
        setUpObservers(viewModel)
        setUpFiltersRecyclerView(binding)
        binding.filterListBtnShowOrders.setOnClickListener {
            viewModel.onShowOrdersClicked()
        }
    }

    private fun setUpFiltersRecyclerView(binding: FragmentOrderFilterListBinding) {
        orderFilterCategoryAdapter = OrderFilterCategoryAdapter(
            onFilterCategoryClicked = { selectedFilterCategory ->
                viewModel.onFilterCategoryClicked(selectedFilterCategory)
            }
        )
        binding.filterList.apply {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = orderFilterCategoryAdapter
            addItemDecoration(
                DividerItemDecoration(
                    requireActivity(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun navigateToFilterOptions() {
        val action = OrderFilterCategoryListFragmentDirections
            .actionOrderFilterListFragmentToOrderFilterOptionListFragment()
        findNavController().navigateSafely(action)
    }

    private fun setUpObservers(viewModel: OrderFilterListViewModel) {
        viewModel.orderFilterCategories.observe(viewLifecycleOwner) {
            showOrderFilters(it)
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ShowOrderStatusFilterOptions -> navigateToFilterOptions()
                else -> event.isHandled = false
            }
        }
        viewModel.orderFilterCategoryViewState.observe(viewLifecycleOwner) { viewState ->
            requireActivity().title = viewState.screenTitle
        }
    }

    private fun showOrderFilters(orderFilters: List<FilterListCategoryUiModel>) {
        orderFilterCategoryAdapter.submitList(orderFilters)
    }
}
