package io.github.sds100.keymapper.util.delegate

import androidx.annotation.MainThread
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.data.model.*
import io.github.sds100.keymapper.domain.usecases.PerformActionsUseCase
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.result.isError
import kotlinx.coroutines.*

/**
 * Created by sds100 on 10/01/21.
 */
abstract class SimpleMappingController(
    private val coroutineScope: CoroutineScope,
    private val useCase: PerformActionsUseCase,
    iConstraintDelegate: IConstraintDelegate,
    iActionError: IActionError
) : IActionError by iActionError, IConstraintDelegate by iConstraintDelegate {

    private val repeatJobs = mutableMapOf<String, List<RepeatJob>>()
    private val performActionJobs = mutableMapOf<String, Job>()
    private val actionsBeingHeldDown = mutableListOf<ActionEntity>()

    val performAction = LiveEvent<PerformAction>()
    val vibrateEvent: LiveEvent<VibrateEvent> = LiveEvent()
    val showTriggeredToastEvent = LiveEvent<ShowTriggeredKeymapToast>()

    fun onDetected(
        mappingId: String,
        actionList: List<ActionEntity>,
        constraintList: List<ConstraintEntity>,
        constraintMode: Int,
        isEnabled: Boolean,
        extras: List<Extra>,
        vibrate: Boolean,
        showTriggeredToast: Boolean
    ) {
        if (!isEnabled) return
        if (actionList.isEmpty()) return
        if (!constraintList.toTypedArray().constraintsSatisfied(constraintMode)) return

        repeatJobs[mappingId]?.forEach { it.cancel() }

        performActionJobs[mappingId]?.cancel()

        performActionJobs[mappingId] = coroutineScope.launch {
            val repeatJobs = mutableListOf<RepeatJob>()

            actionList.forEach {
                if (canActionBePerformed(it, useCase.hasRootPermission).isError) return@forEach

                if (it.repeat) {
                    var alreadyRepeating = false

                    for (job in this@SimpleMappingController.repeatJobs[mappingId] ?: emptyList()) {
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
                        actionsBeingHeldDown.any { action -> action.uid == it.uid }

                    val keyEventAction = when {
                        it.holdDown && !alreadyBeingHeldDown -> InputEventType.DOWN
                        alreadyBeingHeldDown -> InputEventType.UP
                        else -> InputEventType.DOWN_UP
                    }

                    when {
                        it.holdDown -> actionsBeingHeldDown.add(it)
                        alreadyBeingHeldDown -> actionsBeingHeldDown.remove(it)
                    }

                    performAction(it, keyEventAction)
                }

                delay(it.delayBeforeNextAction?.toLong() ?: 0)
            }

            this@SimpleMappingController.repeatJobs[mappingId] = repeatJobs
        }

        //TODO use SimpleMapping interface
//        if (vibrate) {
//            val duration = extras
//                .getData(FingerprintMapEntity.EXTRA_VIBRATION_DURATION)
//                .valueOrNull()
//                ?.toLong()
//                ?: useCase.defaultVibrateDuration.firstBlocking().toLong()
//
//            vibrateEvent.value = VibrateEvent(duration)
//        }

        if (showTriggeredToast) {
            showTriggeredToastEvent.value = ShowTriggeredKeymapToast
        }
    }

    @MainThread
    private fun performAction(
        action: ActionEntity,
        keyEventAction: InputEventType = InputEventType.DOWN_UP
    ) {
        repeat(action.multiplier ?: 1) {
            performAction.value = PerformAction(
                action,
                0,
                keyEventAction)
        }
    }

    private fun repeatAction(action: ActionEntity) = coroutineScope.launch(start = CoroutineStart.LAZY) {
        val repeatRate = action.repeatRate?.toLong() ?: 500L
//            ?: useCase.defaultRepeatRate.firstBlocking().toLong() TODO

        val holdDownDuration = action.holdDownDuration?.toLong()?: 400L
//            ?: useCase.defaultHoldDownDuration.firstBlocking().toLong() TODO

        val holdDown = action.holdDown

        while (true) {
            val keyEventAction = when {
                holdDown -> InputEventType.DOWN
                else -> InputEventType.DOWN_UP
            }

            performAction(action, keyEventAction)

            if (holdDown) {
                delay(holdDownDuration)
                performAction(action, InputEventType.UP)
            }

            delay(repeatRate)
        }
    }

    fun reset() {
        repeatJobs.values.forEach { jobs ->
            jobs.forEach { it.cancel() }
        }

        repeatJobs.clear()

        performActionJobs.values.forEach {
            it.cancel()
        }

        performActionJobs.clear()

        actionsBeingHeldDown.forEach {
            performAction(it, InputEventType.UP)
        }

        actionsBeingHeldDown.clear()
    }

    private class RepeatJob(val actionUuid: String, launch: () -> Job) : Job by launch.invoke()
}
