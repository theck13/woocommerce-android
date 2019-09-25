package com.woocommerce.android.ui.orders

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.extensions.onScrollDown
import com.woocommerce.android.extensions.onScrollUp
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.base.TopLevelFragment
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.main.MainNavigationRouter
import com.woocommerce.android.ui.orders.OrderListAdapter.OnLoadMoreListener
import com.woocommerce.android.util.CurrencyFormatter
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_order_list.*
import kotlinx.android.synthetic.main.fragment_order_list.view.*
import kotlinx.android.synthetic.main.order_list_view.view.*
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.WCOrderStatusModel
import javax.inject.Inject

class OrderListFragment : TopLevelFragment(), OrderListContract.View,
        OrderStatusListView.OrderStatusListListener,
        OnQueryTextListener,
        OnActionExpandListener,
        OnLoadMoreListener, OrderListListener {
    companion object {
        val TAG: String = OrderListFragment::class.java.simpleName
        const val STATE_KEY_LIST = "list-state"
        const val STATE_KEY_REFRESH_PENDING = "is-refresh-pending"
        const val STATE_KEY_ACTIVE_FILTER = "active-order-status-filter"
        const val STATE_KEY_SEARCH_QUERY = "search-query"
        const val STATE_KEY_IS_SEARCHING = "is_searching"
        const val STATE_KEY_IS_FILTER_ENABLED = "is_filter_enabled"

        private const val SEARCH_TYPING_DELAY_MS = 500L

        fun newInstance(orderStatusFilter: String? = null): OrderListFragment {
            val fragment = OrderListFragment()
            fragment.orderStatusFilter = orderStatusFilter
            return fragment
        }
    }

    @Inject lateinit var presenter: OrderListContract.Presenter
    @Inject lateinit var uiMessageResolver: UIMessageResolver
    @Inject lateinit var selectedSite: SelectedSite

    @Inject lateinit var currencyFormatter: CurrencyFormatter

    override var isRefreshPending = true // If true, the fragment will refresh its orders when its visible
    override var isRefreshing: Boolean
        get() = orderRefreshLayout.isRefreshing
        set(_) {}
    override var isSearching: Boolean = false

    private var listState: Parcelable? = null // Save the state of the recycler view
    private var orderStatusFilter: String? = null // Order status filter

    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var searchQuery: String = ""
    private val searchHandler = Handler()

    /**
     * flag to check if the user selected any order status from the order status list
     * If true, the data in the order list tab currently visible, will be refreshed
     */
    private var isFilterEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        savedInstanceState?.let { bundle ->
            listState = bundle.getParcelable(STATE_KEY_LIST)
            isRefreshPending = bundle.getBoolean(STATE_KEY_REFRESH_PENDING, false)
            orderStatusFilter = bundle.getString(STATE_KEY_ACTIVE_FILTER, null)
            isSearching = bundle.getBoolean(STATE_KEY_IS_SEARCHING)
            isFilterEnabled = bundle.getBoolean(STATE_KEY_IS_FILTER_ENABLED)
            searchQuery = bundle.getString(STATE_KEY_SEARCH_QUERY, "")
        }
    }

    // region options menu
    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.menu_order_list_fragment, menu)

        searchMenuItem = menu?.findItem(R.id.menu_search)
        searchView = searchMenuItem?.actionView as SearchView?

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        refreshOptionsMenu()
        super.onPrepareOptionsMenu(menu)
    }

    /**
     * This is a replacement for activity?.invalidateOptionsMenu() since that causes the
     * search menu item to collapse
     */
    private fun refreshOptionsMenu() {
        val showSearch = shouldShowSearchMenuItem()
        searchMenuItem?.let {
            if (it.isActionViewExpanded) it.collapseActionView()
            if (it.isVisible != showSearch) it.isVisible = showSearch
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.menu_search -> {
                AnalyticsTracker.track(Stat.ORDERS_LIST_MENU_SEARCH_TAPPED)
                enableSearchListeners()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isChildFragmentShowing() = (activity as? MainNavigationRouter)?.isChildFragmentShowing() ?: false

    private fun shouldShowSearchMenuItem(): Boolean {
        val isChildShowing = isChildFragmentShowing()
        return when {
            (isChildShowing) -> false
            else -> true
        }
    }
    // endregion

    override fun onAttach(context: Context?) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_order_list, container, false)
        with(view) {
            orderRefreshLayout?.apply {
                activity?.let { activity ->
                    setColorSchemeColors(
                            ContextCompat.getColor(activity, R.color.colorPrimary),
                            ContextCompat.getColor(activity, R.color.colorAccent),
                            ContextCompat.getColor(activity, R.color.colorPrimaryDark)
                    )
                }
                // Set the scrolling view in the custom SwipeRefreshLayout
                scrollUpChild = order_list_view.ordersList
                setOnRefreshListener {
                    AnalyticsTracker.track(Stat.ORDERS_LIST_PULLED_TO_REFRESH)

                    orderRefreshLayout.isRefreshing = false

                    if (!isRefreshPending) {
                        isRefreshPending = true
                        if (isFilterEnabled || !isSearching) {
                            presenter.loadOrders(orderStatusFilter, forceRefresh = true)
                        } else {
                            presenter.searchOrders(searchQuery)
                        }
                    }
                }
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        presenter.takeView(this)

        val tabPosition = AppPrefs.getSelectedOrderListTabPosition()
        resources.getStringArray(R.array.order_list_tabs).toList()
                .forEachIndexed { index, title ->
                    val tab = tab_layout.newTab().apply {
                        text = title
                        tag = title
                    }
                    tab_layout.addTab(tab)

                    // Start with the tab user had previously selected
                    // if no tab is selected, default to the `Processing` Tab
                    if (index == tabPosition) {
                        orderStatusFilter = getOrderStatusByTab(tab)
                        tab.select()
                    }
                }

        order_list_view.init(currencyFormatter = currencyFormatter, orderListListener = this)
        order_list_view.initEmptyView(selectedSite.get())
        order_status_list_view.init(listener = this)

        if (isActive && !deferInit) {
            presenter.loadOrders(orderStatusFilter, forceRefresh = this.isRefreshPending, isFirstRun = true)
        }

        listState?.let {
            order_list_view.onFragmentRestoreInstanceState(it)
            listState = null
        }
        order_list_view.ordersAdapter.setOnLoadMoreListener(this)

        tab_layout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val previousOrderStatus = orderStatusFilter
                orderStatusFilter = getOrderStatusByTab(tab)

                if (orderStatusFilter != previousOrderStatus) {
                    // store the selected tab in SharedPrefs
                    // clear the adapter data
                    // load orders based on the order status
                    AppPrefs.setSelectedOrderListTab(tab.position)
                    order_list_view.clearAdapterData()
                    presenter.loadOrders(orderStatusFilter, true)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                order_list_view.scrollToTop()
            }
        })

        // As part of the new order list design changes, there is no elevation of the toolbar
        activity?.toolbar?.elevation = 0f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(STATE_KEY_LIST, order_list_view.onFragmentSavedInstanceState())
        outState.putBoolean(STATE_KEY_REFRESH_PENDING, isRefreshPending)
        outState.putString(STATE_KEY_ACTIVE_FILTER, orderStatusFilter)
        outState.putBoolean(STATE_KEY_IS_SEARCHING, isSearching)
        outState.putBoolean(STATE_KEY_IS_FILTER_ENABLED, isFilterEnabled)
        outState.putString(STATE_KEY_SEARCH_QUERY, searchQuery)

        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        disableSearchListeners()
        presenter.dropView()
        searchView = null
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        if (hidden) {
            // restore the toolbar elevation when the order list screen is hidden
            activity?.toolbar?.elevation = resources.getDimension(R.dimen.appbar_elevation)
            clearSearchResults()
        } else {
            // silently refresh if this fragment is no longer hidden
            if (isSearching) {
                enableSearchListeners()
                presenter.searchOrders(searchQuery)
            } else {
                // update the toolbar elevation if child fragment is displayed
                activity?.toolbar?.elevation = if (isChildFragmentShowing()) {
                    resources.getDimension(R.dimen.appbar_elevation)
                } else 0f

                disableSearchListeners()
                presenter.fetchAndLoadOrdersFromDb(orderStatusFilter, isForceRefresh = false)
            }
        }
    }

    override fun onReturnedFromChildFragment() {
        showOptionsMenu(true)

        if (isSearching) {
            searchMenuItem?.expandActionView()
            searchView?.setQuery(searchQuery, false)
        } else {
            presenter.loadOrders(orderStatusFilter, forceRefresh = this.isRefreshPending)
        }
    }

    override fun setLoadingMoreIndicator(active: Boolean) {
        order_list_view.setLoadingMoreIndicator(active)
    }

    override fun showSkeleton(show: Boolean) {
        order_list_view.showSkeleton(show)
    }

    override fun showRefreshingIndicator(show: Boolean) {
        orderRefreshLayout?.isRefreshing = show
    }

    override fun showLoading(show: Boolean) {
        if (order_list_view.getOrderListItemCount() > 0) {
            showRefreshingIndicator(show)
        } else {
            showSkeleton(show)
        }
    }

    override fun showOrders(orders: List<WCOrderModel>, filterByStatus: String?, isFreshData: Boolean) {
        // Only update the order list view if the new filter match the currently selected order status
        if (orderStatusFilter == filterByStatus) {
            order_list_view.showOrders(orders, filterByStatus, isFreshData)

            if (isFreshData) {
                isRefreshPending = false
            }

            if (isActive) {
                updateActivityTitle()
            }
        }
    }

    /**
     * User scrolled to the last order and the adapter is requesting us to fetch more orders
     */
    override fun onRequestLoadMore() {
        if (presenter.canLoadMoreOrders() && !presenter.isLoadingOrders()) {
            if (isSearching) {
                presenter.searchMoreOrders(searchQuery)
            } else {
                presenter.loadMoreOrders(orderStatusFilter)
            }
        }
    }

    private fun isShowingAllOrders(): Boolean {
        return !isSearching && orderStatusFilter.isNullOrEmpty()
    }

    private fun isShowingProcessingOrders() = tab_layout.selectedTabPosition == 0

    /**
     * shows the view that appears for stores that have have no orders matching the current filter
     */
    override fun showEmptyView(show: Boolean) {
        if (show) {
            // if the user is searching we show a simple "No matching orders" TextView, otherwise if
            // there isn't a filter (ie: we're showing All orders and there aren't any), then we want
            // to show the full "customers waiting" view, otherwise we show a simple textView stating
            // there aren't any orders
            @StringRes val messageId: Int
            val showImage: Boolean
            val showShareButton: Boolean
            when {
                isSearching -> {
                    showImage = false
                    showShareButton = false
                    messageId = R.string.orders_empty_message_with_search
                }
                isShowingAllOrders() -> {
                    showImage = true
                    showShareButton = true
                    messageId = R.string.waiting_for_customers
                }
                isShowingProcessingOrders() -> {
                    showImage = false
                    showShareButton = false
                    messageId = R.string.orders_empty_message_with_processing
                }
                else -> {
                    showImage = true
                    showShareButton = true
                    messageId = R.string.orders_empty_message_with_filter
                }
            }
            order_list_view.showEmptyView(messageId, showImage, showShareButton)
            isRefreshPending = false
        } else {
            order_list_view.hideEmptyView()
        }
    }

    override fun getFragmentTitle(): String {
        return getString(R.string.orders)
    }

    override fun scrollToTop() {
        order_list_view.scrollToTop()
    }

    override fun refreshFragmentState() {
        isRefreshPending = true
        if (isActive) {
            if (isSearching) {
                presenter.searchOrders(searchQuery)
            } else {
                presenter.loadOrders(orderStatusFilter, forceRefresh = true)
            }
        }
    }

    override fun showLoadOrdersError() {
        uiMessageResolver.getSnack(R.string.orderlist_error_fetch_generic).show()
    }

    override fun showNoConnectionError() {
        uiMessageResolver.getSnack(R.string.error_generic_network).show()
    }

    override fun setOrderStatusOptions(orderStatusOptions: Map<String, WCOrderStatusModel>) {
        order_list_view.setOrderStatusOptions(orderStatusOptions)
    }

    /**
     * We use this to clear the options menu when navigating to a child destination - otherwise this
     * fragment's menu will continue to appear when the child is shown
     */
    private fun showOptionsMenu(show: Boolean) {
        setHasOptionsMenu(show)
        if (show) {
            refreshOptionsMenu()
        }
    }

    override fun showOrderDetail(order: WCOrderModel) {
        disableSearchListeners()
        showOptionsMenu(false)
        (activity as? MainNavigationRouter)?.showOrderDetail(order.localSiteId, order.remoteOrderId)
    }

    override fun onOrderStatusSelected(orderStatus: String?) {
        orderStatusFilter = orderStatus

        if (isAdded) {
            AnalyticsTracker.track(
                    Stat.ORDERS_LIST_FILTER,
                    mapOf(AnalyticsTracker.KEY_STATUS to orderStatus.orEmpty())
            )

            enableFilterListeners()
            order_list_view.clearAdapterData()
            presenter.loadOrders(orderStatusFilter, true)

            updateActivityTitle()
            searchMenuItem?.isVisible = shouldShowSearchMenuItem()
        }
    }
    // endregion

    override fun onFragmentScrollDown() {
        onScrollDown()
    }

    override fun onFragmentScrollUp() {
        onScrollUp()
    }

    override fun getOrderStatusOptions() = presenter.getOrderStatusOptions()

    override fun openOrderDetail(wcOrderModel: WCOrderModel) {
        presenter.openOrderDetail(wcOrderModel)
    }

    override fun refreshOrderStatusOptions() {
        presenter.refreshOrderStatusOptions()
    }

    private fun getOrderStatusByTab(tab: TabLayout.Tab) = if (tab.position == 0) {
        (tab.tag as? String)?.toLowerCase()
    } else null

    // region search
    override fun onQueryTextSubmit(query: String): Boolean {
        submitSearch(query)
        org.wordpress.android.util.ActivityUtils.hideKeyboard(activity)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        // only display the order status list if the search query is empty
        when {
            newText.isEmpty() -> displayOrderStatusListView()
            else -> hideOrderStatusListView()
        }

        if (newText.length > 2) {
            submitSearchDelayed(newText)
        } else {
            order_list_view.clearAdapterData()
        }
        showEmptyView(false)
        return true
    }

    override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
        order_list_view.clearAdapterData()
        tab_layout.visibility = View.GONE
        isSearching = true
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
        clearSearchResults()
        return true
    }

    /**
     * Submit the search after a brief delay unless the query has changed - this is used to
     * perform a search while the user is typing
     */
    override fun submitSearchDelayed(query: String) {
        searchHandler.postDelayed({
            searchView?.let {
                // submit the search if the searchView's query still matches the passed query
                if (query == it.query.toString()) submitSearch(query)
            }
        }, SEARCH_TYPING_DELAY_MS)
    }

    /**
     * Submit the search with no delay
     */
    override fun submitSearch(query: String) {
        AnalyticsTracker.track(
                Stat.ORDERS_LIST_FILTER,
                mapOf(AnalyticsTracker.KEY_SEARCH to query))

        searchQuery = query
        presenter.searchOrders(query)
    }

    /**
     * Presenter received search results, show them in the adapter
     */
    override fun showSearchResults(query: String, orders: List<WCOrderModel>) {
        if (query == searchQuery) {
            org.wordpress.android.util.ActivityUtils.hideKeyboard(activity)
            order_list_view.setOrders(orders)
        }
    }

    /**
     * Presenter received search result with an offset due to infinite scroll, add them to the adapter
     */
    override fun addSearchResults(query: String, orders: List<WCOrderModel>) {
        if (query == searchQuery) {
            order_list_view.addOrders(orders)
        }
    }

    /**
     * Return to the non-search order view
     */
    override fun clearSearchResults() {
        if (isSearching) {
            searchQuery = ""
            isSearching = false
            if (isFilterEnabled) disableFilterListeners()
            disableSearchListeners()
            updateActivityTitle()
            searchMenuItem?.collapseActionView()
            presenter.fetchAndLoadOrdersFromDb(orderStatusFilter, isForceRefresh = false)
        }
    }

    private fun disableSearchListeners() {
        searchMenuItem?.setOnActionExpandListener(null)
        searchView?.setOnQueryTextListener(null)
        hideOrderStatusListView()
        tab_layout.visibility = View.VISIBLE
    }

    private fun enableSearchListeners() {
        searchMenuItem?.setOnActionExpandListener(this)
        searchView?.setOnQueryTextListener(this)
        displayOrderStatusListView()
        order_status_list_view.updateOrderStatusListView(presenter.getOrderStatusList())
    }

    /**
     * Method called when user clicks on an order status from [OrderStatusListView]
     * 1. The order status view is hidden
     * 2. The search is no longer enabled
     * 3. The search query text is updated to display the order status selected.
     * 4. The [isFilterEnabled] flag is set to true.
     *    This is because once an order status is selected and the order list for that status is displayed,
     *    when back is clicked, the order list needs to be refreshed again from the api,
     *    since we only store the orders for a particular status in local cache.
     */
    private fun enableFilterListeners() {
        isFilterEnabled = true
        hideOrderStatusListView()
        searchView?.queryHint = getString(R.string.orders)
                .plus(orderStatusFilter?.let { filter ->
                    val orderStatusLabel = presenter.getOrderStatusOptions()[filter]?.label
                    getString(R.string.orderlist_filtered, orderStatusLabel)
                } ?: "")

        searchView?.findViewById<EditText>(R.id.search_src_text)?.also {
            it.setHintTextColor(Color.WHITE)
            it.isEnabled = false
        }
    }

    /**
     * Method called when user clicks on the back button after selecting an order status
     * 1. The order status view is hidden
     * 2. The search is enabled again and the hint query is updated
     * 3. The order list for the currently displayed tab needs to be refreshed from the api only
     *    if isFilterEnabled is true
     */
    private fun disableFilterListeners() {
        if (isFilterEnabled) {
            searchView?.findViewById<EditText>(R.id.search_src_text)?.also { it.isEnabled = true }
            searchView?.queryHint = getString(R.string.search)

            val tabPosition = AppPrefs.getSelectedOrderListTabPosition()
            orderStatusFilter = tab_layout.getTabAt(tabPosition)?.let { getOrderStatusByTab(it) }

            presenter.loadOrders(orderStatusFilter, forceRefresh = isFilterEnabled)
            isFilterEnabled = false
        }
    }

    private fun displayOrderStatusListView() {
        order_status_list_view.visibility = View.VISIBLE
        orderRefreshLayout.isEnabled = false
        activity?.toolbar?.elevation = resources.getDimension(R.dimen.appbar_elevation)
    }

    private fun hideOrderStatusListView() {
        order_status_list_view.visibility = View.GONE
        orderRefreshLayout.isEnabled = true
        activity?.toolbar?.elevation = 0f
    }
    // endregion
}
