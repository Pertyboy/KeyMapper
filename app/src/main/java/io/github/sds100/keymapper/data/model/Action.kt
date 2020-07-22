package io.github.sds100.keymapper.data.model

import android.content.Context
import com.google.gson.annotations.SerializedName
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.util.ActionType
import io.github.sds100.keymapper.util.ActionUtils
import io.github.sds100.keymapper.util.SystemAction
import io.github.sds100.keymapper.util.result.onSuccess
import splitties.bitflags.withFlag
import java.io.Serializable

/**
 * Created by sds100 on 16/07/2018.
 */

/**
 * @property [data] The information required to perform the action. E.g if the type is [ActionType.APP],
 * the data will be the package name of the application
 *
 * Different Types of actions:
 * - Apps
 * - App shortcuts
 * - Keycode
 * - Key
 * - Insert a block of text
 * - System actions/settings
 */
data class Action(
    @SerializedName(NAME_ACTION_TYPE)
    val type: ActionType,

    /**
     * How each action type saves data:
     *
     * - Apps: package name
     * - App shortcuts: the intent for the shortcut as a parsed URI
     * - Key Event: the keycode. Any extra information is stored in [extras]
     * - Block of text: text to insert
     * - System action: the system action id
     */
    @SerializedName(NAME_DATA)
    val data: String,

    @SerializedName(NAME_EXTRAS)
    val extras: List<Extra> = listOf(),

    @SerializedName(NAME_FLAGS)
    val flags: Int = 0

) : Serializable {
    companion object {

        //DON'T CHANGE THESE. Used for JSON serialization and parsing.
        const val NAME_ACTION_TYPE = "type"
        const val NAME_DATA = "data"
        const val NAME_EXTRAS = "extras"
        const val NAME_FLAGS = "flags"

        const val STOP_REPEAT_BEHAVIOUR_TRIGGER_AGAIN = 0

        //Behaviour flags
        const val ACTION_FLAG_SHOW_VOLUME_UI = 1
        const val ACTION_FLAG_SHOW_PERFORMING_ACTION_TOAST = 2
        const val ACTION_FLAG_REPEAT = 4

        val ACTION_FLAG_LABEL_MAP = mapOf(
            ACTION_FLAG_SHOW_VOLUME_UI to R.string.flag_show_volume_dialog,
            ACTION_FLAG_SHOW_PERFORMING_ACTION_TOAST to R.string.flag_performing_action_toast,
            ACTION_FLAG_REPEAT to R.string.flag_repeat_actions
        )

        //DON'T CHANGE THESE IDs!!!!
        //Data Extras. Extras that are required to perform an action.
        const val EXTRA_SHORTCUT_TITLE = "extra_title"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_STREAM_TYPE = "extra_stream_type"
        const val EXTRA_LENS = "extra_flash"
        const val EXTRA_RINGER_MODE = "extra_ringer_mode"
        const val EXTRA_DND_MODE = "extra_do_not_disturb_mode"

        /**
         * The KeyEvent meta state is stored as bit flags.
         */
        const val EXTRA_KEY_EVENT_META_STATE = "extra_meta_state"

        const val EXTRA_IME_ID = "extra_ime_id"
        const val EXTRA_IME_NAME = "extra_ime_name"

        //Behaviour extras. Extras that tweak how and when an action is performed.
        const val EXTRA_CUSTOM_STOP_REPEAT_BEHAVIOUR = "extra_custom_stop_repeat_behaviour"
        const val EXTRA_REPEAT_DELAY = "extra_hold_down_until_repeat_delay"
        const val EXTRA_REPEAT_RATE = "extra_repeat_delay"

        val DATA_EXTRAS = arrayOf(
            EXTRA_SHORTCUT_TITLE,
            EXTRA_PACKAGE_NAME,
            EXTRA_STREAM_TYPE,
            EXTRA_LENS,
            EXTRA_RINGER_MODE,
            EXTRA_DND_MODE,
            EXTRA_KEY_EVENT_META_STATE,
            EXTRA_IME_ID,
            EXTRA_IME_NAME
        )

        fun appAction(packageName: String): Action {
            return Action(ActionType.APP, packageName)
        }

        fun appShortcutAction(model: AppShortcutModel): Action {
            val extras = mutableListOf(
                Extra(EXTRA_SHORTCUT_TITLE, model.name),
                Extra(EXTRA_PACKAGE_NAME, model.packageName)
            )

            return Action(ActionType.APP_SHORTCUT, data = model.uri, extras = extras)
        }

        fun keyAction(keyCode: Int): Action {
            return keyEventAction(keyCode)
        }

        fun keycodeAction(keyCode: Int): Action {
            return keyEventAction(keyCode)
        }

        private fun keyEventAction(keyCode: Int): Action {
            return Action(ActionType.KEY_EVENT, keyCode.toString(), flags = ACTION_FLAG_REPEAT)
        }

        fun textBlockAction(text: String): Action {
            return Action(ActionType.TEXT_BLOCK, text, flags = ACTION_FLAG_REPEAT)
        }

        fun urlAction(url: String): Action {
            return Action(ActionType.URL, url)
        }

        fun systemAction(ctx: Context, model: SelectedSystemActionModel): Action {
            val data = model.id
            val extras = mutableListOf<Extra>()
            var flags = 0

            model.optionData?.let {
                extras.add(Extra(Option.getExtraIdForOption(model.id), it))

                if (model.id == SystemAction.SWITCH_KEYBOARD) {
                    Option.getOptionLabel(ctx, model.id, it).onSuccess { imeName ->
                        extras.add(Extra(EXTRA_IME_NAME, imeName))
                    }
                }
            }

            //show the volume UI by default when the user chooses a volume action.
            if (ActionUtils.isVolumeAction(data)) {
                flags = flags.withFlag(ACTION_FLAG_SHOW_VOLUME_UI).withFlag(ACTION_FLAG_REPEAT)
            }

            val action = Action(ActionType.SYSTEM_ACTION, data, extras, flags)
            return action
        }
    }

    constructor(type: ActionType, data: String, extra: Extra) : this(type, data, mutableListOf(extra))

    /**
     * A unique identifier describing this action
     */
    val uniqueId: String
        get() = buildString {
            append(type)
            append(data)
            extras.forEach {
                append("${it.id}${it.data}")
            }
            append(flags)
        }

    fun clone(
        type: ActionType = this.type,
        data: String = this.data,
        flags: Int = this.flags,
        extras: List<Extra> = this.extras
    ) = Action(type, data, extras, flags)

    override fun equals(other: Any?) = this.uniqueId == (other as Action?)?.uniqueId
}