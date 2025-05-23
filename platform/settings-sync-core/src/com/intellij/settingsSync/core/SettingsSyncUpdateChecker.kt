package com.intellij.settingsSync.core

import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SettingsSyncUpdateChecker() {

  companion object {
    private val LOG = logger<SettingsSyncUpdateChecker>()
  }

  @RequiresBackgroundThread
  fun scheduleUpdateFromServer() : UpdateResult {
    val updateResult = RemoteCommunicatorHolder.getRemoteCommunicator()?.receiveUpdates() ?: run {
      val errorMsg = "Cannot get update from server - no communicator provider"
      LOG.info(errorMsg)
      return UpdateResult.Error(errorMsg)
    }
    when(updateResult) {
      is UpdateResult.Success -> {
        val snapshot = updateResult.settingsSnapshot
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.CloudChange(snapshot, updateResult.serverVersionId))
      }
      is UpdateResult.FileDeletedFromServer -> {
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeletedOnCloud)
        SettingsSyncEventsStatistics.DISABLED_AUTOMATICALLY.log(SettingsSyncEventsStatistics.AutomaticDisableReason.REMOVED_FROM_SERVER)
      }
      is UpdateResult.NoFileOnServer -> {
        LOG.info("Settings update requested, but there was no file on the server.")
      }
      is UpdateResult.Error -> {
        LOG.warn("Settings update requested, but failed with error: " + updateResult.message)
        SettingsSyncStatusTracker.getInstance().updateOnError(
          SettingsSyncBundle.message("notification.title.update.error") + ": " + updateResult.message)

      }
    }
    return updateResult
  }

}
