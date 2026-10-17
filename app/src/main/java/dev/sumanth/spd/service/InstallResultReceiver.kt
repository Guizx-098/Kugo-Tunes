package dev.sumanth.spd.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dev.sumanth.spd.utils.ApkInstaller

/** Receives the PackageInstaller result and maps it to the update state. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        fail(UpdateError.GENERIC, e.message)
                    }
                } else {
                    fail(UpdateError.GENERIC, message)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateState.stage = UpdateStage.INSTALLED
                ApkInstaller.deleteAll(context)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                UpdateState.stage = UpdateStage.INSTALL_CANCELLED
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                UpdateState.stage = UpdateStage.INVALID_APK
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> fail(UpdateError.CONFLICT, message)
            else -> fail(UpdateError.GENERIC, message)
        }
    }

    private fun fail(error: UpdateError, detail: String?) {
        UpdateState.error = error
        UpdateState.errorDetail = detail
        UpdateState.stage = UpdateStage.INSTALL_ERROR
    }
}
