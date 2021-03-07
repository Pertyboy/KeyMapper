package io.github.sds100.keymapper.util

import android.content.Context
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.AppPreferences
import io.github.sds100.keymapper.data.model.DeviceInfo
import io.github.sds100.keymapper.data.model.Trigger
import io.github.sds100.keymapper.data.model.Trigger.Companion.DOUBLE_PRESS
import io.github.sds100.keymapper.data.model.Trigger.Companion.LONG_PRESS
import io.github.sds100.keymapper.data.model.Trigger.Companion.PARALLEL
import io.github.sds100.keymapper.data.model.Trigger.Companion.SEQUENCE
import io.github.sds100.keymapper.data.model.Trigger.Companion.TRIGGER_FLAG_LABEL_MAP
import io.github.sds100.keymapper.data.model.TriggerKeyModel
import splitties.bitflags.hasFlag

/**
 * Created by sds100 on 02/03/2020.
 */

fun Trigger.getFlagLabelList(ctx: Context): List<String> = sequence {
    TRIGGER_FLAG_LABEL_MAP.keys.forEach { flag ->
        if (flags.hasFlag(flag)) {
            yield(ctx.str(TRIGGER_FLAG_LABEL_MAP.getValue(flag)))
        }
    }
}.toList()

fun Trigger.buildTriggerFlagsDescription(ctx: Context): String = buildString {
    getFlagLabelList(ctx).forEachIndexed { index, label ->
        if (index > 0) {
            append(" ${ctx.str(R.string.interpunct)} ")
        }

        append(label)
    }
}

fun Trigger.buildDescription(ctx: Context, deviceInfoList: List<DeviceInfo>): String = buildString {
    val separator = when (mode) {
        PARALLEL -> ctx.str(R.string.plus)
        SEQUENCE -> ctx.str(R.string.arrow)
        else -> ctx.str(R.string.plus)
    }

    val longPress = ctx.str(R.string.clicktype_long_press)
    val doublePress = ctx.str(R.string.clicktype_double_press)

    keys.forEachIndexed { index, key ->
        if (index > 0) {
            append("  $separator ")
        }

        when (key.clickType) {
            LONG_PRESS -> append(longPress)
            DOUBLE_PRESS -> append(doublePress)
        }

        append(" ${KeyEventUtils.keycodeToString(key.keyCode)}")

        val deviceName = key.getDeviceName(ctx, deviceInfoList)
        append(" (")

        append(deviceName)

        val flagLabels = key.getFlagLabelList(ctx)

        flagLabels.forEach { label ->
            append(" ${ctx.str(R.string.interpunct)} ")
            append(label)
        }

        append(")")
    }
}

fun Trigger.Key.buildModel(ctx: Context, deviceInfoList: List<DeviceInfo>): TriggerKeyModel {

    val extraInfo = buildString {
        append(getDeviceName(ctx, deviceInfoList))

        val flagLabels = getFlagLabelList(ctx)

        flagLabels.forEach { label ->
            append(" ${ctx.str(R.string.interpunct)} ")
            append(label)
        }
    }

    return TriggerKeyModel(
        id = uid,
        keyCode = keyCode,
        name = KeyEventUtils.keycodeToString(keyCode),
        clickType = clickType,
        extraInfo = extraInfo
    )
}

fun Trigger.Key.getDeviceName(ctx: Context, deviceInfoList: List<DeviceInfo>): String =
    when (deviceId) {
        Trigger.Key.DEVICE_ID_THIS_DEVICE -> ctx.str(R.string.this_device)
        Trigger.Key.DEVICE_ID_ANY_DEVICE -> ctx.str(R.string.any_device)
        else -> {
            val deviceInfo = deviceInfoList.find { it.descriptor == deviceId }

            when {
                deviceInfo == null -> ctx.str(R.string.dont_know_device_name)

                AppPreferences.showDeviceDescriptors -> "${deviceInfo.name} ${deviceInfo.descriptor.substring(0..4)}"

                else -> deviceInfo.name
            }
        }
    }

fun Trigger.Key.getFlagLabelList(ctx: Context): List<String> = sequence {
    Trigger.Key.TRIGGER_KEY_FLAG_LABEL_MAP.keys.forEach { flag ->
        if (flags.hasFlag(flag)) {
            yield(ctx.str(Trigger.Key.TRIGGER_KEY_FLAG_LABEL_MAP.getValue(flag)))
        }
    }
}.toList()

val Trigger.triggerFromOtherApps: Boolean
    get() = flags.hasFlag(Trigger.TRIGGER_FLAG_FROM_OTHER_APPS)

val Trigger.vibrate: Boolean
    get() = flags.hasFlag(Trigger.TRIGGER_FLAG_VIBRATE)