package io.github.sds100.keymapper.util

import android.accessibilityservice.FingerprintGestureController
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.FingerprintMap
import splitties.bitflags.hasFlag

/**
 * Created by sds100 on 14/11/20.
 */
object FingerprintMapUtils {
    /**
     * Use version code for 2.2.0.beta.2 because in beta 1 there were issues detecting the
     * availability of fingerprint gestures.
     */
    const val FINGERPRINT_GESTURES_MIN_VERSION = 40

    const val SWIPE_DOWN = "swipe_down"
    const val SWIPE_UP = "swipe_up"
    const val SWIPE_LEFT = "swipe_left"
    const val SWIPE_RIGHT = "swipe_right"

    val GESTURES = arrayOf(SWIPE_DOWN, SWIPE_UP, SWIPE_LEFT, SWIPE_RIGHT)

    val HEADERS = mapOf(
        SWIPE_DOWN to R.string.header_fingerprint_gesture_down,
        SWIPE_UP to R.string.header_fingerprint_gesture_up,
        SWIPE_LEFT to R.string.header_fingerprint_gesture_left,
        SWIPE_RIGHT to R.string.header_fingerprint_gesture_right
    )

    @RequiresApi(Build.VERSION_CODES.O)
    val SDK_ID_TO_KEY_MAPPER_ID = mapOf(
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN to SWIPE_DOWN,
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP to SWIPE_UP,
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT to SWIPE_LEFT,
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT to SWIPE_RIGHT
    )
}

fun FingerprintMap.getFlagLabelList(ctx: Context): List<String> = sequence {
    FingerprintMap.FLAG_LABEL_MAP.keys.forEach { flag ->
        if (flags.hasFlag(flag)) {
            yield(ctx.str(FingerprintMap.FLAG_LABEL_MAP.getValue(flag)))
        }
    }
}.toList()

fun FingerprintMap.buildOptionsDescription(ctx: Context): String = buildString {
    getFlagLabelList(ctx).forEachIndexed { index, label ->
        if (index > 0) {
            append(" ${ctx.str(R.string.middot)} ")
        }

        append(label)
    }
}