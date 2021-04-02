package io.github.sds100.keymapper.ui.activity

import android.Manifest
import android.app.Service
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import io.github.sds100.keymapper.*
import io.github.sds100.keymapper.Constants.PACKAGE_NAME
import io.github.sds100.keymapper.data.viewmodel.BackupRestoreViewModel
import io.github.sds100.keymapper.data.viewmodel.KeyActionTypeViewModel
import io.github.sds100.keymapper.databinding.ActivityHomeBinding
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.result.Error
import io.github.sds100.keymapper.util.result.onFailure
import kotlinx.android.synthetic.main.activity_home.*
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.messageResource
import splitties.alertdialog.appcompat.okButton
import splitties.alertdialog.appcompat.titleResource
import splitties.snackbar.snack
import splitties.toast.toast
import timber.log.Timber

/**
 * Created by sds100 on 19/02/2020.
 */

class MainActivity : AppCompatActivity() {

    companion object {
        const val KEY_SHOW_ACCESSIBILITY_SETTINGS_NOT_FOUND_DIALOG =
            "$PACKAGE_NAME.show_accessibility_settings_not_found_dialog"
    }

    private val keyActionTypeViewModel: KeyActionTypeViewModel by viewModels {
        InjectorUtils.provideKeyActionTypeViewModel()
    }

    private val backupRestoreViewModel: BackupRestoreViewModel by viewModels {
        InjectorUtils.provideBackupRestoreViewModel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataBindingUtil.setContentView<ActivityHomeBinding>(this, R.layout.activity_home)

        if (intent.getBooleanExtra(KEY_SHOW_ACCESSIBILITY_SETTINGS_NOT_FOUND_DIALOG, false)) {
            alertDialog {
                titleResource = R.string.dialog_title_cant_find_accessibility_settings_page
                messageResource = R.string.dialog_message_cant_find_accessibility_settings_page

                okButton {
                    PermissionUtils.requestWriteSecureSettingsPermission(
                        this@MainActivity, findNavController(R.id.container)
                    )
                }

                show()
            }
        }

        if (BuildConfig.DEBUG
            && PermissionUtils.isPermissionGranted(this, Manifest.permission.WRITE_SECURE_SETTINGS)
        ) {
            AccessibilityUtils.enableService(this)
        }

        backupRestoreViewModel.eventStream.observe(this, {
            when (it) {
                is MessageEvent -> toast(str(it.textRes))

                is AutomaticBackupResult ->
                    it.result.onFailure { failure ->
                        if (failure is Error.FileAccessDenied) showFileAccessDeniedSnackBar()
                    }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        ServiceLocator.serviceAdapter(this).updateWhetherServiceIsEnabled()
        ServiceLocator.notificationController(this).invalidateNotifications()
    }

    override fun onDestroy() {
        ServiceLocator.release()

        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let { keyActionTypeViewModel.onKeyDown(it.keyCode) }

        return super.onKeyUp(keyCode, event)
    }

    private fun showFileAccessDeniedSnackBar() {
        coordinatorLayout.snack(R.string.error_file_access_denied_automatic_backup).apply {
            setAction(R.string.reset) {
                container.findNavController().navigate(R.id.action_global_settingsFragment)
            }

            show()
        }
    }
}