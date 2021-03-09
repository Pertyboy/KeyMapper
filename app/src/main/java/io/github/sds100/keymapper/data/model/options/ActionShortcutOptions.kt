package io.github.sds100.keymapper.data.model.options

import io.github.sds100.keymapper.data.model.ActionEntity
import io.github.sds100.keymapper.data.model.options.BoolOption.Companion.saveBoolOption
import io.github.sds100.keymapper.data.model.options.IntOption.Companion.saveIntOption
import io.github.sds100.keymapper.util.*
import kotlinx.android.parcel.Parcelize

/**
 * Created by sds100 on 22/11/20.
 */
@Parcelize
class ActionShortcutOptions(
    override val id: String,
    val showVolumeUi: BoolOption,
    val delayBeforeNextAction: IntOption,
    val multiplier: IntOption
) : BaseOptions<ActionEntity> {

    companion object {
        const val ID_SHOW_VOLUME_UI = "show_volume_ui"
        const val ID_MULTIPLIER = "multiplier"
        const val ID_DELAY_BEFORE_NEXT_ACTION = "delay_before_next_action"
    }

    constructor(action: ActionEntity,
                actionCount: Int) : this(
        id = action.uid,

        showVolumeUi = BoolOption(
            id = ID_SHOW_VOLUME_UI,
            value = action.showVolumeUi,
            isAllowed = ActionUtils.isVolumeAction(action.data)
        ),

        multiplier = IntOption(
            id = ID_MULTIPLIER,
            value = action.multiplier ?: IntOption.DEFAULT,
            isAllowed = true
        ),

        delayBeforeNextAction = IntOption(
            id = ID_DELAY_BEFORE_NEXT_ACTION,
            value = action.delayBeforeNextAction ?: IntOption.DEFAULT,
            isAllowed = actionCount > 0
        )
    )

    override val intOptions = listOf(
        delayBeforeNextAction,
        multiplier
    )

    override val boolOptions = listOf(
        showVolumeUi
    )

    override fun setValue(id: String, value: Int): BaseOptions<ActionEntity> {
        when (id) {
            ID_MULTIPLIER -> multiplier.value = value
            ID_DELAY_BEFORE_NEXT_ACTION -> delayBeforeNextAction.value = value
        }

        return this
    }

    override fun setValue(id: String, value: Boolean): BaseOptions<ActionEntity> {
        when (id) {

            ID_SHOW_VOLUME_UI -> showVolumeUi.value = value
        }

        return this
    }

    override fun apply(old: ActionEntity): ActionEntity {
        val newFlags = old.flags
            .saveBoolOption(showVolumeUi, ActionEntity.ACTION_FLAG_SHOW_VOLUME_UI)

        val newExtras = old.extras
            .saveIntOption(multiplier, ActionEntity.EXTRA_MULTIPLIER)
            .saveIntOption(delayBeforeNextAction, ActionEntity.EXTRA_DELAY_BEFORE_NEXT_ACTION)

        newExtras.removeAll {
            it.id in arrayOf(ActionEntity.EXTRA_CUSTOM_STOP_REPEAT_BEHAVIOUR, ActionEntity.EXTRA_CUSTOM_HOLD_DOWN_BEHAVIOUR)
        }

        return old.copy(flags = newFlags, extras = newExtras, uid = old.uid)
    }
}