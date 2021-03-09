package io.github.sds100.keymapper.data.viewmodel

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.CheckBoxListItemModel
import io.github.sds100.keymapper.data.model.SliderListItemModel
import io.github.sds100.keymapper.data.model.TriggerEntity
import io.github.sds100.keymapper.data.model.options.BoolOption
import io.github.sds100.keymapper.data.model.options.IntOption
import io.github.sds100.keymapper.data.model.options.TriggerKeyOptions

/**
 * Created by sds100 on 27/06/20.
 */
class TriggerKeyOptionsViewModel : BaseOptionsDialogViewModel<TriggerKeyOptions>() {

    override val helpUrl = R.string.url_trigger_key_options_guide
    override val stateKey = "state_trigger_options"

    override fun createSliderListItemModel(option: IntOption): SliderListItemModel {
        throw Exception(
            "Don't know how to create a SliderListItemModel for this option $option.id")
    }

    override fun createCheckboxListItemModel(option: BoolOption) = when (option.id) {
        TriggerKeyOptions.ID_DO_NOT_CONSUME_KEY_EVENT ->
            CheckBoxListItemModel(
                id = option.id,
                label = R.string.flag_dont_override_default_action,
                isChecked = option.value
            )

        else -> throw Exception(
            "Don't know how to create a CheckboxListItemModel for this option $option.id")
    }

    val shortPress = MediatorLiveData<Boolean>().apply {
        addSource(options) {
            val newValue = it.clickType.value == TriggerEntity.SHORT_PRESS

            if (value != newValue) {
                value = newValue
            }
        }
    }

    val longPress = MediatorLiveData<Boolean>().apply {
        addSource(options) {
            val newValue = it.clickType.value == TriggerEntity.LONG_PRESS

            if (value != newValue) {
                value = newValue
            }
        }
    }

    val doublePress = MediatorLiveData<Boolean>().apply {
        addSource(options) {
            val newValue = it.clickType.value == TriggerEntity.DOUBLE_PRESS

            if (value != newValue) {
                value = newValue
            }
        }
    }

    init {
        options.addSource(shortPress) {
            if (it) {
                setValue(TriggerKeyOptions.ID_CLICK_TYPE, TriggerEntity.SHORT_PRESS)
            }
        }

        options.addSource(longPress) {
            if (it) {
                setValue(TriggerKeyOptions.ID_CLICK_TYPE, TriggerEntity.LONG_PRESS)
            }
        }

        options.addSource(doublePress) {
            if (it) {
                setValue(TriggerKeyOptions.ID_CLICK_TYPE, TriggerEntity.DOUBLE_PRESS)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return TriggerKeyOptionsViewModel() as T
        }
    }
}