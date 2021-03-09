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
class FingerprintActionOptions(
    override val id: String,
    private val showVolumeUi: BoolOption,
    private val delayBeforeNextAction: IntOption,
    private val multiplier: IntOption,
    private val repeatUntilSwipedAgain: BoolOption,
    private val repeatRate: IntOption,
    private val holdDownUntilSwipedAgain: BoolOption,
    private val holdDownDuration: IntOption

) : BaseOptions<ActionEntity> {

    companion object {
        const val ID_SHOW_VOLUME_UI = "show_volume_ui"
        const val ID_MULTIPLIER = "multiplier"
        const val ID_DELAY_BEFORE_NEXT_ACTION = "delay_before_next_action"
        const val ID_REPEAT_UNTIL_SWIPED_AGAIN = "repeat_until_swiped_again"
        const val ID_REPEAT_RATE = "repeat_rate"
        const val ID_HOLD_DOWN_UNTIL_SWIPED_AGAIN = "hold_down_until_swiped_again"
        const val ID_HOLD_DOWN_DURATION = "hold_down_duration"
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
        ),

        repeatUntilSwipedAgain = BoolOption(
            id = ID_REPEAT_UNTIL_SWIPED_AGAIN,
            value = action.repeat,
            isAllowed = actionCount > 0
        ),

        repeatRate = IntOption(
            id = ID_REPEAT_RATE,
            value = action.repeatRate ?: IntOption.DEFAULT,
            isAllowed = action.repeat
        ),

        holdDownUntilSwipedAgain = BoolOption(
            id = ID_HOLD_DOWN_UNTIL_SWIPED_AGAIN,
            value = action.holdDown,
            isAllowed = action.canBeHeldDown
        ),

        holdDownDuration = IntOption(
            id = ID_HOLD_DOWN_DURATION,
            value = action.holdDownDuration ?: IntOption.DEFAULT,
            isAllowed = action.repeat && action.holdDown
        )
    )

    override val intOptions = listOf(
        delayBeforeNextAction,
        multiplier,
        repeatRate,
        holdDownDuration
    )

    override val boolOptions = listOf(
        showVolumeUi,
        repeatUntilSwipedAgain,
        holdDownUntilSwipedAgain
    )

    override fun setValue(id: String, value: Int): BaseOptions<ActionEntity> {
        when (id) {
            ID_REPEAT_RATE -> repeatRate.value = value
            ID_MULTIPLIER -> multiplier.value = value
            ID_DELAY_BEFORE_NEXT_ACTION -> delayBeforeNextAction.value = value
            ID_HOLD_DOWN_DURATION -> holdDownDuration.value = value
        }

        return this
    }

    override fun setValue(id: String, value: Boolean): BaseOptions<ActionEntity> {
        when (id) {
            ID_REPEAT_UNTIL_SWIPED_AGAIN -> {
                repeatUntilSwipedAgain.value = value
                repeatRate.isAllowed = value

                holdDownDuration.isAllowed =
                    holdDownUntilSwipedAgain.value && repeatUntilSwipedAgain.value
            }

            ID_SHOW_VOLUME_UI -> showVolumeUi.value = value
            ID_HOLD_DOWN_UNTIL_SWIPED_AGAIN -> {
                holdDownUntilSwipedAgain.value = value

                holdDownDuration.isAllowed =
                    holdDownUntilSwipedAgain.value && repeatUntilSwipedAgain.value
            }
        }

        return this
    }

    override fun apply(old: ActionEntity): ActionEntity {
        val newFlags = old.flags
            .saveBoolOption(showVolumeUi, ActionEntity.ACTION_FLAG_SHOW_VOLUME_UI)
            .saveBoolOption(repeatUntilSwipedAgain, ActionEntity.ACTION_FLAG_REPEAT)
            .saveBoolOption(holdDownUntilSwipedAgain, ActionEntity.ACTION_FLAG_HOLD_DOWN)

        val newExtras = old.extras
            .saveIntOption(multiplier, ActionEntity.EXTRA_MULTIPLIER)
            .saveIntOption(delayBeforeNextAction, ActionEntity.EXTRA_DELAY_BEFORE_NEXT_ACTION)
            .saveIntOption(repeatRate, ActionEntity.EXTRA_REPEAT_RATE)
            .saveIntOption(holdDownDuration, ActionEntity.EXTRA_HOLD_DOWN_DURATION)

        return old.copy(flags = newFlags, extras = newExtras, uid = old.uid)
    }
}