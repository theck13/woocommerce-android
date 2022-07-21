package com.woocommerce.android.ui.login.localnotifications

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R
import com.woocommerce.android.model.Notification
import com.woocommerce.android.push.NotificationChannelType
import com.woocommerce.android.push.WooNotificationBuilder
import com.woocommerce.android.push.WooNotificationType
import com.woocommerce.android.support.HelpActivity
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.Companion.LOGIN_HELP_NOTIFICATION_ID
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.Companion.LOGIN_NOTIFICATION_TYPE_KEY
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.LoginSupportNotificationType
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.LoginSupportNotificationType.DEFAULT_SUPPORT
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.LoginSupportNotificationType.LOGIN_SITE_ADDRESS_ERROR
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler.LoginSupportNotificationType.valueOf
import com.woocommerce.android.viewmodel.ResourceProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wooNotificationBuilder: WooNotificationBuilder,
    private val resourceProvider: ResourceProvider,
    private val prefsWrapper: AppPrefsWrapper
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        when (getNotificationType()) {
            DEFAULT_SUPPORT -> defaultLoginSupportNotification()
            LOGIN_SITE_ADDRESS_ERROR -> siteAddressErrorNotification()
        }
        prefsWrapper.setPreLoginNotificationDisplayed(displayed = true)
        return Result.success()
    }

    private fun defaultLoginSupportNotification() {
        wooNotificationBuilder.buildAndDisplayLoginHelpNotification(
            notificationLocalId = LOGIN_HELP_NOTIFICATION_ID,
            appContext.getString(R.string.notification_channel_pre_login_id),
            notification = buildDefaultLoginNotification(),
            notificationTappedIntent = buildOpenSupportScreenIntent()
        )
    }

    private fun siteAddressErrorNotification() {
        wooNotificationBuilder.buildAndDisplayLoginHelpNotification(
            notificationLocalId = LOGIN_HELP_NOTIFICATION_ID,
            appContext.getString(R.string.notification_channel_pre_login_id),
            notification = buildDefaultLoginNotification(),
            notificationTappedIntent = buildOpenSupportScreenIntent(),
            actions = getActionsForSiteAddressErrorNotification()
        )
    }

    private fun buildDefaultLoginNotification() = Notification(
        noteId = LOGIN_HELP_NOTIFICATION_ID,
        uniqueId = 0,
        remoteNoteId = 0,
        remoteSiteId = 0,
        icon = null,
        noteTitle = resourceProvider.getString(R.string.login_local_notification_no_interaction_title),
        noteMessage = resourceProvider.getString(R.string.login_local_notification_no_interaction_description),
        noteType = WooNotificationType.PRE_LOGIN,
        channelType = NotificationChannelType.PRE_LOGIN
    )

    private fun buildOpenSupportScreenIntent(): Intent =
        HelpActivity.createIntent(appContext, HelpActivity.Origin.LOGIN_LOCAL_NOTIFICATION, null)

    private fun getActionsForSiteAddressErrorNotification(): List<Pair<String, Intent>> =
        listOf(
            resourceProvider.getString(R.string.login_local_notification_contact_support_button)
                to buildOpenSupportScreenIntent()
        )

    private fun getNotificationType(): LoginSupportNotificationType = runCatching {
        valueOf(inputData.getString(LOGIN_NOTIFICATION_TYPE_KEY).orEmpty())
    }.getOrDefault(DEFAULT_SUPPORT)
}
