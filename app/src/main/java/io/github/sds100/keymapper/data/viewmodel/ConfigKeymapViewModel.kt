package io.github.sds100.keymapper.data.viewmodel

import android.os.Bundle
import androidx.lifecycle.*
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.data.model.Action
import io.github.sds100.keymapper.data.model.Constraint
import io.github.sds100.keymapper.data.model.KeyMap
import io.github.sds100.keymapper.data.model.Trigger
import io.github.sds100.keymapper.data.model.options.KeymapActionOptions
import io.github.sds100.keymapper.data.usecase.ConfigKeymapUseCase
import io.github.sds100.keymapper.domain.usecases.CreateTriggerUseCase
import io.github.sds100.keymapper.domain.usecases.OnboardingUseCase
import io.github.sds100.keymapper.domain.usecases.ShowActionsUseCase
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * Created by sds100 on 22/11/20.
 */

class ConfigKeymapViewModel(
    private val keymapRepository: ConfigKeymapUseCase,
    showActionsUseCase: ShowActionsUseCase,
    createTriggerUseCase: CreateTriggerUseCase,
    onboardingUseCase: OnboardingUseCase
) : ViewModel(), IConfigMappingViewModel {

    companion object {
        const val NEW_KEYMAP_ID = -2L
        private const val STATE_KEY = "config_keymap"
    }

    private var id = NEW_KEYMAP_ID

    private val _uid = MutableLiveData<String>()
    val uid: LiveData<String> = _uid

    override val actionListViewModel =
        object : ActionListViewModel<KeymapActionOptions>(viewModelScope, showActionsUseCase) {
            override val stateKey = "keymap_action_list_view_model"

            override fun getActionOptions(action: Action): KeymapActionOptions {
                return KeymapActionOptions(
                    action,
                    actionList.value!!.size,
                    triggerViewModel.mode.value,
                    triggerViewModel.keys.value
                )
            }

            override fun onAddAction(action: Action) {
                if (action.type == ActionType.KEY_EVENT) {
                    getActionOptions(action).apply {
                        if (KeyEventUtils.isModifierKey(action.data.toInt())) {
                            setValue(KeymapActionOptions.ID_HOLD_DOWN, true)
                            setValue(KeymapActionOptions.ID_REPEAT, false)
                        } else {
                            setValue(KeymapActionOptions.ID_REPEAT, true)
                        }

                        setOptions(this)
                    }
                }
            }
        }

    val triggerViewModel = TriggerViewModel(
        viewModelScope,
        onboardingUseCase,
        createTriggerUseCase,
        this.uid
    )

    private val supportedConstraints =
        Constraint.COMMON_SUPPORTED_CONSTRAINTS.toMutableList().apply {
            add(Constraint.SCREEN_ON)
            add(Constraint.SCREEN_OFF)
        }.toList()

    val constraintListViewModel = ConstraintListViewModel(viewModelScope, supportedConstraints)

    override val isEnabled = MutableLiveData(false)

    private val _eventStream = LiveEvent<Event>().apply {
        addSource(constraintListViewModel.eventStream) {
            when (it) {
                is FixFailure -> value = it
            }
        }

        addSource(actionListViewModel.eventStream) {
            when (it) {
                is FixFailure, is EnableAccessibilityServicePrompt -> value = it
            }
        }

        addSource(triggerViewModel.eventStream) {
            when (it) {
                is FixFailure, is EnableAccessibilityServicePrompt -> value = it
            }
        }
    }

    override val eventStream: LiveData<Event> = _eventStream

    init {
        actionListViewModel.setActionList(emptyList())
        triggerViewModel.setTrigger(Trigger())
        constraintListViewModel.setConstraintList(emptyList(), Constraint.DEFAULT_MODE)
        isEnabled.value = true
        _uid.value = UUID.randomUUID().toString()

        triggerViewModel.mode.observeForever {
            actionListViewModel.invalidateOptions()
        }

        triggerViewModel.keys.observeForever {
            actionListViewModel.invalidateOptions()
        }
    }

    override fun save() {
        val keymap = createKeymap()

        if (id == NEW_KEYMAP_ID) {
            keymapRepository.insertKeymap(keymap.copy(id = 0))
        } else {
            keymapRepository.updateKeymap(keymap)
        }
    }

    override fun saveState(outState: Bundle) {
        outState.putParcelable(STATE_KEY, createKeymap())
    }

    @Suppress("UNCHECKED_CAST")
    override fun restoreState(state: Bundle) {
        state.getParcelable<KeyMap>(STATE_KEY)?.let {
            loadKeymap(it)
        }
    }

    private fun createKeymap(): KeyMap {
        val trigger = triggerViewModel.createTrigger()

        return KeyMap(
            id = id,
            trigger = trigger ?: Trigger(),
            actionList = actionListViewModel.actionList.value ?: listOf(),
            constraintList = constraintListViewModel.constraintList.value ?: listOf(),
            constraintMode = constraintListViewModel.getConstraintMode(),
            isEnabled = isEnabled.value ?: true,
            uid = uid.value ?: UUID.randomUUID().toString()
        )
    }

    fun loadKeymap(id: Long) {
        if (id == NEW_KEYMAP_ID) return

        viewModelScope.launch {
            val keymap = keymapRepository.getKeymap(id)
            loadKeymap(keymap)
        }
    }

    private fun loadKeymap(keymap: KeyMap) {
        id = keymap.id

        /* this must be before everything else because action options might be deselected if
        * there is no trigger. issue #593 */
        triggerViewModel.setTrigger(keymap.trigger)

        actionListViewModel.setActionList(keymap.actionList)
        constraintListViewModel.setConstraintList(keymap.constraintList, keymap.constraintMode)
        isEnabled.value = keymap.isEnabled
        _uid.value = keymap.uid
    }

    class Factory(
        private val configKeymapUseCase: ConfigKeymapUseCase,
        private val onboardingUseCase: OnboardingUseCase,
        private val showActionsUseCase: ShowActionsUseCase,
        private val createTriggerUseCase: CreateTriggerUseCase,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>) =
            ConfigKeymapViewModel(
                configKeymapUseCase,
                showActionsUseCase,
                createTriggerUseCase,
                onboardingUseCase
            ) as T
    }
}