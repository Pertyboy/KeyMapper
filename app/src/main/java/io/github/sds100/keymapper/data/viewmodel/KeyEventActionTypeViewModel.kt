package io.github.sds100.keymapper.data.viewmodel

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.lifecycle.*
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.data.model.CheckBoxListItemModel
import io.github.sds100.keymapper.domain.devices.DeviceInfo
import io.github.sds100.keymapper.domain.devices.GetDeviceNameUseCase
import io.github.sds100.keymapper.util.BuildDeviceInfoModels
import io.github.sds100.keymapper.util.ChooseKeycode
import io.github.sds100.keymapper.util.Event
import io.github.sds100.keymapper.util.KeyEventUtils
import io.github.sds100.keymapper.util.result.Error
import splitties.bitflags.hasFlag
import splitties.bitflags.minusFlag
import splitties.bitflags.withFlag

/**
 * Created by sds100 on 30/03/2020.
 */


//TODO move to CreateKeyEventActionUseCase
class KeyEventActionTypeViewModel(
    private val getDeviceNameUseCase: GetDeviceNameUseCase
) : ViewModel() {

    val keyCode = MutableLiveData<String>(null)

    val keyCodeLabel: LiveData<String> = keyCode.map {
        if (it.isNullOrEmpty()) return@map ""

        //if it isn't a valid int. E.g if it is too big.
        it.toIntOrNull() ?: return@map ""

        return@map if (it.toInt() > KeyEvent.getMaxKeyCode()) {
            "Key Code $it"
        } else {
            KeyEvent.keyCodeToString(it.toInt())
        }
    }

    val chosenDevice = MutableLiveData<DeviceInfo?>(null)

    val deviceInfoModels = MutableLiveData<List<DeviceInfo>>()

    val metaState = MutableLiveData(0)

    val useShell = MutableLiveData(false)

    private val _eventStream = LiveEvent<Event>()
    val eventStream: LiveData<Event> = _eventStream

    val failure = keyCode.map {
        when {
            it.isNullOrEmpty() -> Error.CantBeEmpty
            it.toIntOrNull() == null -> Error.InvalidNumber

            else -> null
        }
    }

    val isValidKeyCode = failure.map {
        it == null
    }

    val modifierKeyModels = metaState.map {
        KeyEventUtils.MODIFIER_LABELS.map {
            CheckBoxListItemModel(
                id = it.key.toString(),
                label = it.value,
                isChecked = metaState.value?.hasFlag(it.key) == true
            )
        }
    }

    init {
        refreshDevices()
    }

    fun chooseKeycode() {
        _eventStream.value = ChooseKeycode()
    }

    fun setModifierKey(flag: Int, isChecked: Boolean) {
        if (isChecked) {
            metaState.value = metaState.value?.withFlag(flag)
        } else {
            metaState.value = metaState.value?.minusFlag(flag)
        }
    }

    @SuppressLint("NullSafeMutableLiveData")
    fun chooseNoDevice() {
        chosenDevice.value = null
    }

    fun chooseDevice(index: Int) {
        deviceInfoModels.value?.getOrNull(index)?.let {
            chosenDevice.value = it
        }
    }

    fun refreshDevices() {
        _eventStream.value = BuildDeviceInfoModels()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val getDeviceNameUseCase: GetDeviceNameUseCase
    ) : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return KeyEventActionTypeViewModel(getDeviceNameUseCase) as T
        }
    }
}