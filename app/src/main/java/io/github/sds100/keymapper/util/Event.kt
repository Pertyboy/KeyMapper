/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sds100.keymapper.util

import android.os.Parcelable
import androidx.annotation.StringRes
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.*
import io.github.sds100.keymapper.data.model.options.BaseOptions
import io.github.sds100.keymapper.data.model.options.TriggerKeyOptions
import io.github.sds100.keymapper.util.result.Failure
import io.github.sds100.keymapper.util.result.Result
import kotlinx.android.parcel.Parcelize

sealed class Event

open class MessageEvent(@StringRes val textRes: Int) : Event()

class FixFailure(val failure: Failure) : Event()
class VibrateEvent(val duration: Long) : Event()
object ShowTriggeredKeymapToast : Event()
data class PerformAction(
    val action: Action,
    val additionalMetaState: Int = 0,
    val keyEventAction: KeyEventAction = KeyEventAction.DOWN_UP
) : Event()

data class ImitateButtonPress(
    val keyCode: Int,
    val metaState: Int = 0,
    val deviceId: Int = 0,
    val keyEventAction: KeyEventAction,
    val scanCode: Int = 0
) : Event()

class ChoosePackage : Event()
class ChooseBluetoothDevice : Event()
class OpenUrl(val url: String) : Event()
class OpenUrlRes(@StringRes val url: Int) : Event()
class CloseDialog : Event()
class SelectScreenshot : Event()
class ChooseKeycode : Event()
class BuildDeviceInfoModels : Event()
class RequestBackupSelectedKeymaps : Event()

class BuildKeymapListModels(
    val keymapList: List<KeyMap>,
    val deviceInfoList: List<DeviceInfo>,
    val hasRootPermission: Boolean,
    val showDeviceDescriptors: Boolean
) : Event()

class OkDialog(val responseKey: String, @StringRes val message: Int) : Event()
class EnableAccessibilityServicePrompt : Event()
class BackupRequest<T>(val model: T) : Event()
class RequestRestore : Event()
class RequestBackupAll : Event()
class ShowErrorMessage(val failure: Failure) : Event()
class BuildIntentExtraListItemModels(val extraModels: List<IntentExtraModel>) : Event()
class CreateKeymapShortcutEvent(
    val uuid: String,
    val actionList: List<Action>,
    val deviceInfoList: List<DeviceInfo>,
    val showDeviceDescriptors: Boolean
) : Event()

data class SaveEvent<T>(val model: T) : Event()

sealed class ResultEvent<T> : Event() {
    abstract val result: Result<T>
}

data class BackupResult(override val result: Result<Unit>) : ResultEvent<Unit>()
data class RestoreResult(override val result: Result<Unit>) : ResultEvent<Unit>()
data class AutomaticBackupResult(override val result: Result<Unit>) : ResultEvent<Unit>()

object OnBootEvent : Event(), UpdateNotificationEvent

@Parcelize
data class RecordedTriggerKeyEvent(
    val keyCode: Int,
    val deviceName: String,
    val deviceDescriptor: String,
    val isExternal: Boolean
) : Event(), Parcelable

class OnIncrementRecordTriggerTimer(val timeLeft: Int) : Event()
object OnStoppedRecordingTrigger : Event()
object OnAccessibilityServiceStarted : Event(), UpdateNotificationEvent
object OnAccessibilityServiceStopped : Event(), UpdateNotificationEvent
object OnHideKeyboard : Event(), UpdateNotificationEvent
object OnShowKeyboard : Event(), UpdateNotificationEvent

//trigger
class BuildTriggerKeyModels(
    val source: List<Trigger.Key>,
    val deviceInfoList: List<DeviceInfo>,
    val showDeviceDescriptors: Boolean
) : Event()

class EditTriggerKeyOptions(val options: TriggerKeyOptions) : Event()
class EnableCapsLockKeyboardLayoutPrompt : Event()
class StartRecordingTriggerInService : Event()
class StopRecordingTriggerInService : Event()

//action list
class BuildActionListModels(
    val source: List<Action>,
    val deviceInfoList: List<DeviceInfo>,
    val hasRootPermission: Boolean,
    val showDeviceDescriptors: Boolean
) : Event()

class TestAction(val action: Action) : Event()
class EditActionOptions(val options: BaseOptions<Action>) : Event()

//constraints
class DuplicateConstraints : MessageEvent(R.string.error_duplicate_constraint)
class BuildConstraintListModels(val source: List<Constraint>) : Event()
class SelectConstraint(val constraint: Constraint) : Event()

//fingerprint gesture maps
class BuildFingerprintMapModels(
    val maps: Map<String, FingerprintMap>,
    val deviceInfoList: List<DeviceInfo>,
    val hasRootPermission: Boolean,
    val showDeviceDescriptors: Boolean
) : Event()

class BackupFingerprintMaps : Event()
class RequestFingerprintMapReset : Event()

//menu
class OpenSettings : Event()
class OpenAbout : Event()
class ChooseKeyboard : Event()
class SendFeedback : Event()
class EnableAccessibilityService : Event()

//notifications
object ShowFingerprintFeatureNotification : Event(), UpdateNotificationEvent
object DismissFingerprintFeatureNotification : Event(), UpdateNotificationEvent
class DismissNotification(val id: Int) : Event(), UpdateNotificationEvent
interface UpdateNotificationEvent

//home
object ShowWhatsNewEvent : Event()