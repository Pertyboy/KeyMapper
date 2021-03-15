package io.github.sds100.keymapper.domain.mappings.keymap

import io.github.sds100.keymapper.domain.constraints.ConfigConstraintUseCaseImpl
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Created by sds100 on 16/02/2021.
 */
class ConfigKeymapUseCaseImpl : ConfigKeymapUseCase {

    private val keymap = MutableStateFlow<DataState<KeyMap>>(Loading())

    override val state =
        keymap.map { state -> state.mapData { ConfigKeymapState(it.uid, it.isEnabled) } }

    val configTrigger = ConfigKeymapTriggerUseCaseImpl(keymap, ::setKeymap)
    val configActions = ConfigKeymapActionsUseCaseImpl(keymap, ::setKeymap)

    val configConstraints = ConfigConstraintUseCaseImpl()

    override fun loadBlankKeymap() {
        setKeymap(KeyMap())
    }

    override fun setKeymap(keymap: KeyMap) {
        this.keymap.value = Data(keymap)
    }

    override fun getKeymap() = keymap.value

    override fun setEnabled(enabled: Boolean) = editKeymap { it.copy(isEnabled = enabled) }

    private fun editKeymap(block: (keymap: KeyMap) -> KeyMap) {
        keymap.value.ifIsData { setKeymap(block.invoke(it)) }
    }
}

interface ConfigKeymapUseCase  {
    val state: Flow<DataState<ConfigKeymapState>>
    fun setEnabled(enabled: Boolean)

    fun loadBlankKeymap()

    fun setKeymap(keymap: KeyMap)
    fun getKeymap(): DataState<KeyMap>
}

data class ConfigKeymapState(
    val uid: String,
    val isEnabled: Boolean
)