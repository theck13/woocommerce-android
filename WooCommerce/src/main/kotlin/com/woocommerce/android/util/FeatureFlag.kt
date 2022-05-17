package com.woocommerce.android.util

import android.content.Context

/**
 * "Feature flags" are used to hide in-progress features from release versions
 */
enum class FeatureFlag {
    DB_DOWNGRADE,
    CARD_READER,
    JETPACK_CP,
    ORDER_FILTERS,
    ANALYTICS_HUB,
    IN_PERSON_PAYMENTS_CANADA,
    MORE_MENU_INBOX,
    COUPONS_M2;

    fun isEnabled(context: Context? = null): Boolean {
        return when (this) {
            DB_DOWNGRADE -> {
                PackageUtils.isDebugBuild() || context != null && PackageUtils.isBetaBuild(context)
            }
            JETPACK_CP,
            CARD_READER -> true // Keeping the flag for a few sprints so we can quickly disable the feature if needed
            ORDER_FILTERS,
            ANALYTICS_HUB,
            IN_PERSON_PAYMENTS_CANADA,
            MORE_MENU_INBOX,
            COUPONS_M2 -> PackageUtils.isDebugBuild()
        }
    }
}
