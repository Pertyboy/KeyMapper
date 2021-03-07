package io.github.sds100.keymapper.util.delegate

import androidx.annotation.MainThread
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.IConstraintDelegate
import io.github.sds100.keymapper.data.AppPreferences
import io.github.sds100.keymapper.data.model.*
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.result.isFailure
import io.github.sds100.keymapper.util.result.valueOrNull
import kotlinx.coroutines.*
import splitties.bitflags.hasFlag

/**
 * Created by sds100 on 10/01/21.
 */
abstract class SimpleMappingController(
    private val mCoroutineScope: CoroutineScope,
    iConstraintDelegate: IConstraintDelegate,
    iActionError: IActionError
) : IActionError by iActionError, IConstraintDelegate by iConstraintDelegate {

    private val mRepeatJobs = mutableMapOf<String, List<RepeatJob>>()
    private val mPerformActionJobs = mutableMapOf<String, Job>()
    private val mActionsBeingHeldDown = mutableListOf<Action>()

    val performAction = LiveEvent<PerformAction>()
    val vibrate: LiveEvent<Vibrate> = LiveEvent()

    fun onDetected(
        mappingId: String,
        actionList: List<Action>,
        constraintList: List<Constraint>,
        constraintMode: Int,
        isEnabled: Boolean,
        flags: Int,
        extras: List<Extra>
    ) {
        if (!isEnabled) return
        if (actionList.isEmpty()) return
        if (!constraintList.toTypedArray().constraintsSatisfied(constraintMode)) return

        mRepeatJobs[mappingId]?.forEach { it.cancel() }

        mPerformActionJobs[mappingId]?.cancel()

        mPerformActionJobs[mappingId] = mCoroutineScope.launch {
            val repeatJobs = mutableListOf<RepeatJob>()

            actionList.forEach {
                if (canActionBePerformed(it).isFailure) return@forEach

                if (it.repeat) {
                    var alreadyRepeating = false

                    for (job in mRepeatJobs[mappingId] ?: emptyList()) {
                        if (job.actionUuid == it.uid) {
                            alreadyRepeating = true
                            job.cancel()
                            break
                        }
                    }

                    if (!alreadyRepeating) {
                        val job = RepeatJob(it.uid) { repeatAction(it) }
                        repeatJobs.add(job)
                        job.start()
                    }
                } else {

                    val alreadyBeingHeldDown =
                        mActionsBeingHeldDown.any { action -> action.uid == it.uid }

                    val keyEventAction = when {
                        it.holdDown && !alreadyBeingHeldDown -> KeyEventAction.DOWN
                        alreadyBeingHeldDown -> KeyEventAction.UP
                        else -> KeyEventAction.DOWN_UP
                    }

                    when {
                        it.holdDown -> mActionsBeingHeldDown.add(it)
                        alreadyBeingHeldDown -> mActionsBeingHeldDown.remove(it)
                    }

                    performAction(it, keyEventAction)
                }

                delay(it.delayBeforeNextAction?.toLong() ?: 0)
            }

            mRepeatJobs[mappingId] = repeatJobs
        }

        if (flags.hasFlag(FingerprintMap.FLAG_VIBRATE)) {
            val duration = extras.getData(FingerprintMap.EXTRA_VIBRATION_DURATION)
                .valueOrNull()?.toLong() ?: AppPreferences.vibrateDuration.toLong()

            vibrate.value = Vibrate(duration)
        }
    }

    @MainThread
    private fun performAction(
        action: Action,
        keyEventAction: KeyEventAction = KeyEventAction.DOWN_UP
    ) {
        repeat(action.multiplier ?: 1) {
            performAction.value = PerformAction(
                action,
                action.showPerformingActionToast,
                0,
                keyEventAction)
        }
    }

    private fun repeatAction(action: Action) = mCoroutineScope.launch(start = CoroutineStart.LAZY) {
        val repeatRate = action.repeatRate?.toLong() ?: AppPreferences.repeatRate.toLong()

        val holdDownDuration = action.holdDownDuration?.toLong()
            ?: AppPreferences.holdDownDuration.toLong()

        val holdDown = action.holdDown

        while (true) {
            val keyEventAction = when {
                holdDown -> KeyEventAction.DOWN
                else -> KeyEventAction.DOWN_UP
            }

            performAction(action, keyEventAction)

            if (holdDown) {
                delay(holdDownDuration)
                performAction(action, KeyEventAction.UP)
            }

            delay(repeatRate)
        }
    }

    fun reset() {
        mRepeatJobs.values.forEach { jobs ->
            jobs.forEach { it.cancel() }
        }

        mRepeatJobs.clear()

        mPerformActionJobs.values.forEach {
            it.cancel()
        }

        mPerformActionJobs.clear()

        mActionsBeingHeldDown.forEach {
            performAction(it, KeyEventAction.UP)
        }

        mActionsBeingHeldDown.clear()
    }

    private class RepeatJob(val actionUuid: String, launch: () -> Job) : Job by launch.invoke()
}
