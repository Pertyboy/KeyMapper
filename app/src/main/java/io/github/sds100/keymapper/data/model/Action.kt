package io.github.sds100.keymapper.data.model

import android.content.Context
import android.os.Parcelable
import com.github.salomonbrys.kotson.*
import com.google.gson.annotations.SerializedName
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.util.ActionType
import io.github.sds100.keymapper.util.ActionUtils
import io.github.sds100.keymapper.util.SystemAction
import io.github.sds100.keymapper.util.result.onSuccess
import kotlinx.android.parcel.Parcelize
import splitties.bitflags.withFlag
import java.util.*

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
@Parcelize
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
     * - Tap coordinate: comma separated x and y valuesk
     */
    @SerializedName(NAME_DATA)
    val data: String,

    @SerializedName(NAME_EXTRAS)
    val extras: List<Extra> = listOf(),

    @SerializedName(NAME_FLAGS)
    val flags: Int = 0,

    @SerializedName(NAME_UID)
    val uid: String = UUID.randomUUID().toString()

) : Parcelable {
    companion object {

        //DON'T CHANGE THESE. Used for JSON serialization and parsing.
        const val NAME_ACTION_TYPE = "type"
        const val NAME_DATA = "data"
        const val NAME_EXTRAS = "extras"
        const val NAME_FLAGS = "flags"
        const val NAME_UID = "uid"

        const val STOP_REPEAT_BEHAVIOUR_TRIGGER_PRESSED_AGAIN = 0
        const val STOP_HOLD_DOWN_BEHAVIOR_TRIGGER_PRESSED_AGAIN = 0

        //Behaviour flags
        const val ACTION_FLAG_SHOW_VOLUME_UI = 1
        const val ACTION_FLAG_SHOW_PERFORMING_ACTION_TOAST = 2
        const val ACTION_FLAG_REPEAT = 4
        const val ACTION_FLAG_HOLD_DOWN = 8

        val ACTION_FLAG_LABEL_MAP = mapOf(
            ACTION_FLAG_SHOW_VOLUME_UI to R.string.flag_show_volume_dialog,
            ACTION_FLAG_SHOW_PERFORMING_ACTION_TOAST to R.string.flag_performing_action_toast,
            ACTION_FLAG_REPEAT to R.string.flag_repeat_actions,
            ACTION_FLAG_HOLD_DOWN to R.string.flag_hold_down
        )

        //DON'T CHANGE THESE IDs!!!!
        const val EXTRA_SHORTCUT_TITLE = "extra_title"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_STREAM_TYPE = "extra_stream_type"
        const val EXTRA_LENS = "extra_flash"
        const val EXTRA_RINGER_MODE = "extra_ringer_mode"
        const val EXTRA_DND_MODE = "extra_do_not_disturb_mode"
        const val EXTRA_ORIENTATIONS = "extra_orientations"
        const val EXTRA_COORDINATE_DESCRIPTION = "extra_coordinate_description"

        /**
         * The KeyEvent meta state is stored as bit flags.
         */
        const val EXTRA_KEY_EVENT_META_STATE = "extra_meta_state"
        const val EXTRA_KEY_EVENT_DEVICE_DESCRIPTOR = "extra_device_descriptor"

        const val EXTRA_IME_ID = "extra_ime_id"
        const val EXTRA_IME_NAME = "extra_ime_name"

        const val EXTRA_CUSTOM_STOP_REPEAT_BEHAVIOUR = "extra_custom_stop_repeat_behaviour"
        const val EXTRA_CUSTOM_HOLD_DOWN_BEHAVIOUR = "extra_custom_hold_down_behaviour"
        const val EXTRA_REPEAT_DELAY = "extra_hold_down_until_repeat_delay"
        const val EXTRA_REPEAT_RATE = "extra_repeat_delay"
        const val EXTRA_MULTIPLIER = "extra_multiplier"
        const val EXTRA_DELAY_BEFORE_NEXT_ACTION = "extra_delay_before_next_action"
        const val EXTRA_HOLD_DOWN_DURATION = "extra_hold_down_duration"

        fun appAction(packageName: String): Action {
            return Action(ActionType.APP, packageName)
        }

        fun appShortcutAction(name: String, packageName: String?, uri: String): Action {
            val extras = mutableListOf(
                Extra(EXTRA_SHORTCUT_TITLE, name),
            )

            packageName?.let {
                extras.add(Extra(EXTRA_PACKAGE_NAME, packageName))
            }

            return Action(ActionType.APP_SHORTCUT, data = uri, extras = extras)
        }

        fun keyAction(keyCode: Int): Action {
            return keyEventAction(keyCode, metaState = 0)
        }

        fun keyCodeAction(keyCode: Int): Action {
            return keyEventAction(keyCode, metaState = 0)
        }

        fun keyEventAction(keyCode: Int, metaState: Int, deviceDescriptor: String? = null): Action {
            val extras = sequence {
                yield(Extra(EXTRA_KEY_EVENT_META_STATE, metaState.toString()))

                deviceDescriptor?.let {
                    yield(Extra(EXTRA_KEY_EVENT_DEVICE_DESCRIPTOR, it))
                }
            }.toList()

            return Action(
                ActionType.KEY_EVENT,
                keyCode.toString(),
                extras = extras
            )
        }

        fun textBlockAction(text: String): Action {
            return Action(ActionType.TEXT_BLOCK, text, flags = ACTION_FLAG_REPEAT)
        }

        fun urlAction(url: String): Action {
            return Action(ActionType.URL, url)
        }

        fun systemAction(ctx: Context, id: String, optionData: String?): Action {
            val extras = mutableListOf<Extra>()
            var flags = 0

            optionData?.let {
                extras.add(Extra(Option.getExtraIdForOption(id), it))

                if (id == SystemAction.SWITCH_KEYBOARD) {
                    Option.getOptionLabel(ctx, id, it).onSuccess { imeName ->
                        extras.add(Extra(EXTRA_IME_NAME, imeName))
                    }
                }
            }

            //show the volume UI by default when the user chooses a volume action.
            if (ActionUtils.isVolumeAction(id)) {
                flags = flags.withFlag(ACTION_FLAG_SHOW_VOLUME_UI).withFlag(ACTION_FLAG_REPEAT)
            }

            val action = Action(ActionType.SYSTEM_ACTION, id, extras, flags)
            return action
        }

        fun tapCoordinateAction(x: Int, y: Int, coordinateDescription: String?): Action {
            val extras = mutableListOf<Extra>()

            if (!coordinateDescription.isNullOrBlank()) {
                extras.add(Extra(EXTRA_COORDINATE_DESCRIPTION, coordinateDescription))
            }

            return Action(
                ActionType.TAP_COORDINATE,
                "$x,$y",
                extras = extras
            )
        }

        val DESERIALIZER = jsonDeserializer {
            val typeString by it.json.byString(NAME_ACTION_TYPE)
            val type = ActionType.valueOf(typeString)

            val data by it.json.byString(NAME_DATA)

            val extrasJsonArray by it.json.byArray(NAME_EXTRAS)
            val extraList = it.context.deserialize<List<Extra>>(extrasJsonArray) ?: listOf()

            val flags by it.json.byInt(NAME_FLAGS)

            val uid by it.json.byNullableString(NAME_UID)

            Action(type, data, extraList.toMutableList(), flags, uid
                ?: UUID.randomUUID().toString())
        }
    }

    constructor(type: ActionType, data: String, extra: Extra
    ) : this(type, data, mutableListOf(extra))
}