package io.github.sds100.keymapper.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.KeyEvent
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.*
import io.github.sds100.keymapper.service.KeyMapperImeService
import io.github.sds100.keymapper.util.SystemActionUtils.getDescriptionWithOption
import io.github.sds100.keymapper.util.result.*
import splitties.bitflags.hasFlag

/**
 * Created by sds100 on 03/09/2018.
 */

object ActionUtils {

    fun isVolumeAction(actionData: String): Boolean {
        return listOf(
            SystemAction.VOLUME_DECREASE_STREAM,
            SystemAction.VOLUME_INCREASE_STREAM,
            SystemAction.VOLUME_DOWN,
            SystemAction.VOLUME_UP,
            SystemAction.VOLUME_MUTE,
            SystemAction.VOLUME_TOGGLE_MUTE,
            SystemAction.VOLUME_UNMUTE
        ).contains(actionData)
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
fun Action.buildModel(ctx: Context): ActionModel {
    var title: String? = null
    var icon: Drawable? = null

    val error = getTitle(ctx).onSuccess { title = it }
        .then { getIcon(ctx).onSuccess { icon = it } }
        .then { canBePerformed(ctx) }
        .failureOrNull()

    val flags = if (flags == 0) {
        null
    } else {
        buildString {
            val flagLabels = getFlagLabelList(ctx)

            flagLabels.forEachIndexed { index, label ->
                if (index != 0) {
                    append(" ${ctx.str(R.string.interpunct)} ")
                }

                append(label)
            }
        }
    }

    return ActionModel(uniqueId, type, title, icon, flags, error, error?.getBriefMessage(ctx))
}

fun Action.buildChipModel(ctx: Context): ActionChipModel {
    var title: String? = null
    var icon: Drawable? = null

    val error = getTitle(ctx).onSuccess { title = it }
        .then { getIcon(ctx).onSuccess { icon = it } }
        .then { canBePerformed(ctx) }
        .failureOrNull()

    val description = buildString {
        val flagLabels = getFlagLabelList(ctx)

        if (title == null) {
            append(error?.getBriefMessage(ctx))
        } else {
            append(title)
        }

        flagLabels.forEach {
            append(" • $it")
        }
    }

    return ActionChipModel(uniqueId, type, description, error, icon)
}

private fun Action.getTitle(ctx: Context): Result<String> = when (type) {
    ActionType.APP -> {
        try {
            val applicationInfo = ctx.packageManager.getApplicationInfo(data, PackageManager.GET_META_DATA)

            val applicationLabel = ctx.packageManager.getApplicationLabel(applicationInfo)

            Success(ctx.str(R.string.description_open_app, applicationLabel.toString()))
        } catch (e: PackageManager.NameNotFoundException) {
            //the app isn't installed
            AppNotFound(data)
        }
    }

    ActionType.APP_SHORTCUT -> {
        extras.getData(Action.EXTRA_SHORTCUT_TITLE)
    }

    ActionType.KEY_EVENT -> {
        val key = KeyEvent.keyCodeToString(data.toInt())
        Success(ctx.str(R.string.description_keycode, key))
    }

    ActionType.TEXT_BLOCK -> {
        val text = data
        Success(ctx.str(R.string.description_text_block, text))
    }

    ActionType.URL -> {
        Success(ctx.str(R.string.description_url, data))
    }

    ActionType.SYSTEM_ACTION -> {
        val systemActionId = data

        SystemActionUtils.getSystemActionDef(systemActionId) then { systemActionDef ->
            if (systemActionDef.hasOptions) {

                extras.getData(Option.getExtraIdForOption(systemActionId)) then {
                    Option.getOptionLabel(ctx, systemActionId, it)

                } then {
                    Success(systemActionDef.getDescriptionWithOption(ctx, it))

                } otherwise {
                    if (systemActionId == SystemAction.SWITCH_KEYBOARD) {

                        extras.getData(Action.EXTRA_IME_NAME) then {
                            Success(systemActionDef.getDescriptionWithOption(ctx, it))
                        }

                    } else {
                        Success(ctx.str(systemActionDef.descriptionRes))
                    }
                }
            } else {
                Success(ctx.str(systemActionDef.descriptionRes))
            }
        }
    }
}

/**
 * Get the icon for any Action
 */
private fun Action.getIcon(ctx: Context): Result<Drawable?> = when (type) {
    ActionType.APP -> {
        try {
            Success(ctx.packageManager.getApplicationIcon(data))
        } catch (e: PackageManager.NameNotFoundException) {
            //if the app isn't installed, it can't find the icon for it
            AppNotFound(data)
        }
    }

    ActionType.APP_SHORTCUT -> extras.getData(Action.EXTRA_PACKAGE_NAME).then {
        Success(ctx.packageManager.getApplicationIcon(it))
    }

    ActionType.SYSTEM_ACTION -> {
        //convert the string representation of the enum entry into an enum object
        val systemActionId = data

        SystemActionUtils.getSystemActionDef(systemActionId).then {
            Success(null)
            Success(ctx.drawable(it.iconRes))
        }
    }

    else -> Success(null)
}

/**
 * @return if the action can't be performed, it returns an error code.
 * returns null if their if the action can be performed.
 */
fun Action.canBePerformed(ctx: Context): Result<Action> {
    //the action has no data
    if (data.isEmpty()) return NoActionData()

    if (requiresIME) {
        if (!KeyMapperImeService.isServiceEnabled()) {
            return ImeServiceDisabled()
        }

        if (!KeyMapperImeService.isInputMethodChosen()) {
            return ImeServiceNotChosen()
        }
    }

    when (type) {
        ActionType.APP, ActionType.APP_SHORTCUT -> {
            val packageName: Result<String> =
                if (type == ActionType.APP) {
                    Success(data)
                } else {
                    extras.getData(Action.EXTRA_PACKAGE_NAME)
                }

            return packageName.then {
                try {
                    val appInfo = ctx.packageManager.getApplicationInfo(it, 0)

                    //if the app is disabled, show an error message because it won't open
                    if (!appInfo.enabled) {
                        return@then AppDisabled(data)
                    }

                    return@then Success(this)

                } catch (e: Exception) {
                    return@then AppNotFound(data)
                }
            }
        }

        ActionType.SYSTEM_ACTION -> {
            SystemActionUtils.getSystemActionDef(data).onSuccess { systemActionDef ->

                //If an activity to open doesn't exist, the app crashes.
                if (systemActionDef.id == SystemAction.OPEN_VOICE_ASSISTANT) {
                    val activityExists =
                        Intent(Intent.ACTION_VOICE_COMMAND).resolveActivityInfo(ctx.packageManager, 0) != null

                    if (!activityExists) {
                        return GoogleAppNotFound()
                    }
                }

                if (Build.VERSION.SDK_INT < systemActionDef.minApi) {
                    return SdkVersionTooLow(systemActionDef.minApi)
                }

                if (Build.VERSION.SDK_INT > systemActionDef.maxApi) {
                    return SdkVersionTooHigh(systemActionDef.maxApi)
                }

                systemActionDef.permissions.forEach { permission ->
                    if (!PermissionUtils.isPermissionGranted(permission)) {
                        return PermissionDenied(permission)
                    }
                }

                for (feature in systemActionDef.features) {
                    if (!ctx.packageManager.hasSystemFeature(feature)) {
                        return FeatureUnavailable(feature)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (systemActionDef.id == SystemAction.TOGGLE_FLASHLIGHT
                        || systemActionDef.id == SystemAction.ENABLE_FLASHLIGHT
                        || systemActionDef.id == SystemAction.DISABLE_FLASHLIGHT) {

                        extras.getData(Action.EXTRA_LENS).onSuccess { lensOptionId ->
                            val sdkLensId = Option.OPTION_ID_SDK_ID_MAP[lensOptionId]
                                ?: error("Can't find sdk id for that option id")

                            if (!CameraUtils.hasFlashFacing(sdkLensId)) {

                                when (lensOptionId) {
                                    Option.LENS_FRONT -> FrontFlashNotFound()
                                    Option.LENS_BACK -> BackFlashNotFound()
                                }
                            }
                        }
                    }
                }

                if (systemActionDef.id == SystemAction.SWITCH_KEYBOARD) {

                    extras.getData(Action.EXTRA_IME_ID).onSuccess { imeId ->
                        if (!KeyboardUtils.inputMethodExists(imeId)) {
                            var errorData = imeId

                            extras.getData(Action.EXTRA_IME_NAME).onSuccess { imeName ->
                                errorData = imeName
                            }

                            return ImeNotFound(errorData)
                        }
                    }
                }
            }
        }
    }

    return Success(this)
}

/**
 * A string representation of all the extras in an [Action] that are necessary to perform it.
 */
val Action.dataExtraString: String
    get() = buildString {
        Action.DATA_EXTRAS.forEach {
            extras.getData(it).onSuccess { data ->
                append("$it$data")
            }
        }
    }

val Action.requiresIME: Boolean
    get() {
        return type == ActionType.KEY_EVENT ||
            type == ActionType.TEXT_BLOCK ||
            data == SystemAction.MOVE_CURSOR_TO_END
    }

fun Action.getFlagLabelList(ctx: Context): List<String> = sequence {
    Action.ACTION_FLAG_LABEL_MAP.keys.forEach { flag ->
        if (flags.hasFlag(flag)) {
            yield(ctx.str(Action.ACTION_FLAG_LABEL_MAP.getValue(flag)))
        }
    }
}.toList()