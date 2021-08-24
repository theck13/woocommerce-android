package com.woocommerce.android.cardreader.internal.firmware

import com.woocommerce.android.cardreader.firmware.SoftwareUpdateAvailability
import com.woocommerce.android.cardreader.firmware.SoftwareUpdateAvailability.Initializing
import com.woocommerce.android.cardreader.firmware.SoftwareUpdateStatus
import com.woocommerce.android.cardreader.internal.firmware.actions.CheckSoftwareUpdatesAction
import com.woocommerce.android.cardreader.internal.firmware.actions.CheckSoftwareUpdatesAction.CheckSoftwareUpdates
import com.woocommerce.android.cardreader.internal.firmware.actions.InstallAvailableSoftwareUpdateAction
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

internal class SoftwareUpdateManager(
    private val checkUpdatesAction: CheckSoftwareUpdatesAction,
    private val installAvailableSoftwareUpdateAction: InstallAvailableSoftwareUpdateAction
) {
    suspend fun updateSoftware() = flow {
        emit(SoftwareUpdateStatus.Initializing)

        when (val updateStatus = checkUpdatesAction.checkUpdates()) {
            CheckSoftwareUpdates.UpToDate -> emit(SoftwareUpdateStatus.UpToDate)
            is CheckSoftwareUpdates.Failed -> emit(SoftwareUpdateStatus.Failed(updateStatus.e.errorMessage))
            is CheckSoftwareUpdates.UpdateAvailable -> installUpdate()
        }
    }

    suspend fun softwareUpdateStatus() = flow {
        emit(Initializing)

        when (checkUpdatesAction.checkUpdates()) {
            CheckSoftwareUpdates.UpToDate -> emit(SoftwareUpdateAvailability.UpToDate)
            is CheckSoftwareUpdates.Failed -> emit(SoftwareUpdateAvailability.CheckForUpdatesFailed)
            is CheckSoftwareUpdates.UpdateAvailable -> emit(SoftwareUpdateAvailability.UpdateAvailable)
        }
    }

    private suspend fun FlowCollector<SoftwareUpdateStatus>.installUpdate() {
        installAvailableSoftwareUpdateAction.installUpdate().collect { status ->
            when (status) {
                is InstallAvailableSoftwareUpdateAction.InstallSoftwareUpdateStatus.Failed -> emit(
                    SoftwareUpdateStatus.Failed(
                        status.e.errorMessage
                    )
                )
                is InstallAvailableSoftwareUpdateAction.InstallSoftwareUpdateStatus.Installing -> emit(
                    SoftwareUpdateStatus.Installing(status.progress)
                )
                InstallAvailableSoftwareUpdateAction.InstallSoftwareUpdateStatus.Success -> emit(
                    SoftwareUpdateStatus.Success
                )
            }
        }
    }
}
