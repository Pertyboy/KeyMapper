package io.github.sds100.keymapper.util.delegate

import android.view.KeyEvent
import androidx.annotation.MainThread
import androidx.collection.SparseArrayCompat
import androidx.collection.keyIterator
import androidx.collection.set
import androidx.collection.valueIterator
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.IConstraintDelegate
import io.github.sds100.keymapper.data.model.*
import io.github.sds100.keymapper.data.model.Trigger.Companion.DOUBLE_PRESS
import io.github.sds100.keymapper.data.model.Trigger.Companion.LONG_PRESS
import io.github.sds100.keymapper.data.model.Trigger.Companion.SHORT_PRESS
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.result.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.bitflags.hasFlag
import splitties.bitflags.minusFlag
import splitties.bitflags.withFlag

/**
 * Created by sds100 on 05/05/2020.
 */

class KeymapDetectionDelegate(
    private val mCoroutineScope: CoroutineScope,
    val preferences: KeymapDetectionPreferences,
    iClock: IClock,
    iActionError: IActionError,
    iConstraintDelegate: IConstraintDelegate
) : IClock by iClock, IActionError by iActionError,
    IConstraintDelegate by iConstraintDelegate {

    companion object {

        //the states for keys awaiting a double press
        private const val NOT_PRESSED = -1
        private const val SINGLE_PRESSED = 0
        private const val DOUBLE_PRESSED = 1

        private const val INDEX_TRIGGER_LONG_PRESS_DELAY = 0
        private const val INDEX_TRIGGER_DOUBLE_PRESS_DELAY = 1
        private const val INDEX_TRIGGER_SEQUENCE_TRIGGER_TIMEOUT = 2
        private const val INDEX_TRIGGER_VIBRATE_DURATION = 3

        private val TRIGGER_EXTRA_INDEX_MAP = mapOf(
            Trigger.EXTRA_LONG_PRESS_DELAY to INDEX_TRIGGER_LONG_PRESS_DELAY,
            Trigger.EXTRA_DOUBLE_PRESS_DELAY to INDEX_TRIGGER_DOUBLE_PRESS_DELAY,
            Trigger.EXTRA_SEQUENCE_TRIGGER_TIMEOUT to INDEX_TRIGGER_SEQUENCE_TRIGGER_TIMEOUT,
            Trigger.EXTRA_VIBRATION_DURATION to INDEX_TRIGGER_VIBRATE_DURATION
        )

        private const val INDEX_ACTION_REPEAT_RATE = 0
        private const val INDEX_ACTION_REPEAT_DELAY = 1
        private const val INDEX_STOP_REPEAT_BEHAVIOR = 2
        private const val INDEX_ACTION_MULTIPLIER = 3
        private const val INDEX_HOLD_DOWN_BEHAVIOR = 4
        private const val INDEX_DELAY_BEFORE_NEXT_ACTION = 5
        private const val INDEX_HOLD_DOWN_DURATION = 6

        private val ACTION_EXTRA_INDEX_MAP = mapOf(
            Action.EXTRA_REPEAT_RATE to INDEX_ACTION_REPEAT_RATE,
            Action.EXTRA_REPEAT_DELAY to INDEX_ACTION_REPEAT_DELAY,
            Action.EXTRA_CUSTOM_STOP_REPEAT_BEHAVIOUR to INDEX_STOP_REPEAT_BEHAVIOR,
            Action.EXTRA_MULTIPLIER to INDEX_ACTION_MULTIPLIER,
            Action.EXTRA_CUSTOM_HOLD_DOWN_BEHAVIOUR to INDEX_HOLD_DOWN_BEHAVIOR,
            Action.EXTRA_DELAY_BEFORE_NEXT_ACTION to INDEX_DELAY_BEFORE_NEXT_ACTION,
            Action.EXTRA_HOLD_DOWN_DURATION to INDEX_HOLD_DOWN_DURATION
        )

        /**
         * @return whether the actions assigned to this trigger will be performed on the down event of the final key
         * rather than the up event.
         */
        fun performActionOnDown(triggerKeys: List<Trigger.Key>, triggerMode: Int): Boolean {
            return (triggerKeys.size <= 1
                && triggerKeys.getOrNull(0)?.clickType != DOUBLE_PRESS
                && triggerMode == Trigger.UNDEFINED)

                || triggerMode == Trigger.PARALLEL
        }
    }

    /**
     * A cached copy of the keymaps in the database
     */
    var keyMapListCache: List<KeyMap> = listOf()
        set(value) {
            mActionMap.clear()

            // If there are no keymaps with actions then keys don't need to be detected.
            if (!value.any { it.actionList.isNotEmpty() }) {
                field = value
                mDetectKeymaps = false
                return
            }

            if (value.all { !it.isEnabled }) {
                mDetectKeymaps = false
                return
            }

            if (value.isEmpty()) {
                mDetectKeymaps = false
            } else {
                mDetectKeymaps = true

                val longPressSequenceEvents = mutableListOf<Pair<Event, Int>>()

                val doublePressEvents = mutableListOf<TriggerKeyLocation>()

                setActionMapAndOptions(value.flatMap { it.actionList }.toSet())

                // Extract all the external device descriptors used in enabled keymaps because the list is used later
                val sequenceTriggerEvents = mutableListOf<Array<Event>>()
                val sequenceTriggerActions = mutableListOf<IntArray>()
                val sequenceTriggerFlags = mutableListOf<Int>()
                val sequenceTriggerOptions = mutableListOf<IntArray>()
                val sequenceTriggerConstraints = mutableListOf<Array<Constraint>>()
                val sequenceTriggerConstraintMode = mutableListOf<Int>()
                val sequenceTriggerKeyFlags = mutableListOf<IntArray>()

                val parallelTriggerEvents = mutableListOf<Array<Event>>()
                val parallelTriggerActions = mutableListOf<IntArray>()
                val parallelTriggerFlags = mutableListOf<Int>()
                val parallelTriggerOptions = mutableListOf<IntArray>()
                val parallelTriggerConstraints = mutableListOf<Array<Constraint>>()
                val parallelTriggerConstraintMode = mutableListOf<Int>()
                val parallelTriggerModifierKeyIndices = mutableListOf<Pair<Int, Int>>()
                val parallelTriggerKeyFlags = mutableListOf<IntArray>()

                for (keyMap in value) {
                    // ignore the keymap if it has no action.
                    if (keyMap.actionList.isEmpty()) {
                        continue
                    }

                    if (!keyMap.isEnabled) {
                        continue
                    }

                    //TRIGGER STUFF

                    val eventList = mutableListOf<Event>()

                    keyMap.trigger.keys.forEachIndexed { keyIndex, key ->
                        val sequenceTriggerIndex = sequenceTriggerEvents.size

                        if (keyMap.trigger.mode == Trigger.SEQUENCE && key.clickType == LONG_PRESS) {

                            if (keyMap.trigger.keys.size > 1) {
                                longPressSequenceEvents.add(
                                    Event(
                                        key.keyCode,
                                        key.clickType,
                                        key.deviceId
                                    ) to sequenceTriggerIndex
                                )
                            }
                        }

                        if ((keyMap.trigger.mode == Trigger.SEQUENCE || keyMap.trigger.mode == Trigger.UNDEFINED)
                            && key.clickType == DOUBLE_PRESS
                        ) {
                            doublePressEvents.add(
                                TriggerKeyLocation(
                                    sequenceTriggerIndex,
                                    keyIndex
                                )
                            )
                        }

                        when (key.deviceId) {
                            Trigger.Key.DEVICE_ID_THIS_DEVICE -> {
                                mDetectInternalEvents = true
                            }

                            Trigger.Key.DEVICE_ID_ANY_DEVICE -> {
                                mDetectInternalEvents = true
                                mDetectExternalEvents = true
                            }

                            else -> {
                                mDetectExternalEvents = true
                            }
                        }

                        eventList.add(Event(key.keyCode, key.clickType, key.deviceId))
                    }

                    val encodedActionList = encodeActionList(keyMap.actionList)

                    if (keyMap.actionList.any { it.mappedToModifier }) {
                        mModifierKeyEventActions = true
                    }

                    if (keyMap.actionList.any { it.type == ActionType.KEY_EVENT && !it.mappedToModifier }) {
                        mNotModifierKeyEventActions = true
                    }

                    val triggerOptionsArray = IntArray(TRIGGER_EXTRA_INDEX_MAP.size) { -1 }

                    TRIGGER_EXTRA_INDEX_MAP.forEach { pair ->
                        val extraId = pair.key
                        val indexToStore = pair.value

                        keyMap.trigger.extras.getData(extraId).onSuccess {
                            triggerOptionsArray[indexToStore] = it.toInt()
                        }
                    }

                    val constraints = keyMap.constraintList.toTypedArray()

                    if (performActionOnDown(keyMap.trigger.keys, keyMap.trigger.mode)) {
                        parallelTriggerEvents.add(eventList.toTypedArray())
                        parallelTriggerActions.add(encodedActionList)
                        parallelTriggerFlags.add(keyMap.trigger.flags)
                        parallelTriggerOptions.add(triggerOptionsArray)
                        parallelTriggerConstraints.add(constraints)
                        parallelTriggerConstraintMode.add(keyMap.constraintMode)
                        parallelTriggerKeyFlags.add(keyMap.trigger.keys.map { it.flags }
                            .toIntArray())

                    } else {
                        sequenceTriggerEvents.add(eventList.toTypedArray())
                        sequenceTriggerActions.add(encodedActionList)
                        sequenceTriggerFlags.add(keyMap.trigger.flags)
                        sequenceTriggerOptions.add(triggerOptionsArray)
                        sequenceTriggerConstraints.add(constraints)
                        sequenceTriggerConstraintMode.add(keyMap.constraintMode)
                        sequenceTriggerKeyFlags.add(keyMap.trigger.keys.map { it.flags }
                            .toIntArray())
                    }
                }

                val sequenceTriggersOverlappingSequenceTriggers =
                    MutableList(sequenceTriggerEvents.size) { mutableSetOf<Int>() }

                for ((triggerIndex, trigger) in sequenceTriggerEvents.withIndex()) {

                    otherTriggerLoop@ for ((otherTriggerIndex, otherTrigger) in sequenceTriggerEvents.withIndex()) {

                        for ((eventIndex, event) in trigger.withIndex()) {
                            var lastMatchedIndex: Int? = null

                            for (otherIndex in otherTrigger.indices) {
                                if (otherTrigger.hasEventAtIndex(event, otherIndex)) {

                                    //the other trigger doesn't overlap after the first element
                                    if (otherIndex == 0) continue@otherTriggerLoop

                                    //make sure the overlap retains the order of the trigger
                                    if (lastMatchedIndex != null && lastMatchedIndex != otherIndex - 1) {
                                        continue@otherTriggerLoop
                                    }

                                    if (eventIndex == trigger.lastIndex) {
                                        sequenceTriggersOverlappingSequenceTriggers[triggerIndex].add(
                                            otherTriggerIndex
                                        )
                                    }

                                    lastMatchedIndex = otherIndex
                                }
                            }
                        }
                    }
                }

                val sequenceTriggersOverlappingParallelTriggers =
                    MutableList(parallelTriggerEvents.size) { mutableSetOf<Int>() }

                for ((triggerIndex, trigger) in parallelTriggerEvents.withIndex()) {

                    otherTriggerLoop@ for ((otherTriggerIndex, otherTrigger) in sequenceTriggerEvents.withIndex()) {

                        for ((eventIndex, event) in trigger.withIndex()) {
                            var lastMatchedIndex: Int? = null

                            for (otherIndex in otherTrigger.indices) {
                                if (otherTrigger.hasEventAtIndex(event, otherIndex)) {

                                    //the other trigger doesn't overlap after the first element
                                    if (otherIndex == 0) continue@otherTriggerLoop

                                    //make sure the overlap retains the order of the trigger
                                    if (lastMatchedIndex != null && lastMatchedIndex != otherIndex - 1) {
                                        continue@otherTriggerLoop
                                    }

                                    if (eventIndex == trigger.lastIndex) {
                                        sequenceTriggersOverlappingParallelTriggers[triggerIndex].add(
                                            otherTriggerIndex
                                        )
                                    }

                                    lastMatchedIndex = otherIndex
                                }
                            }
                        }
                    }
                }

                val parallelTriggersOverlappingParallelTriggers =
                    MutableList(parallelTriggerEvents.size) { mutableSetOf<Int>() }

                for ((triggerIndex, trigger) in parallelTriggerEvents.withIndex()) {

                    otherTriggerLoop@ for ((otherTriggerIndex, otherTrigger) in parallelTriggerEvents.withIndex()) {

                        for ((eventIndex, event) in trigger.withIndex()) {
                            var lastMatchedIndex: Int? = null

                            for (otherIndex in otherTrigger.indices) {
                                if (otherTrigger.hasEventAtIndex(event, otherIndex)) {

                                    //the other trigger doesn't overlap after the first element
                                    if (otherIndex == 0) continue@otherTriggerLoop

                                    //make sure the overlap retains the order of the trigger
                                    if (lastMatchedIndex != null && lastMatchedIndex != otherIndex - 1) {
                                        continue@otherTriggerLoop
                                    }

                                    if (eventIndex == trigger.lastIndex) {
                                        parallelTriggersOverlappingParallelTriggers[triggerIndex].add(
                                            otherTriggerIndex
                                        )
                                    }

                                    lastMatchedIndex = otherIndex
                                }
                            }
                        }
                    }
                }

                parallelTriggerEvents.forEachIndexed { triggerIndex, events ->
                    events.forEachIndexed { eventIndex, event ->
                        if (isModifierKey(event.keyCode)) {
                            parallelTriggerModifierKeyIndices.add(triggerIndex to eventIndex)
                        }
                    }
                }

                mDetectSequenceTriggers = sequenceTriggerEvents.isNotEmpty()
                mSequenceTriggerEvents = sequenceTriggerEvents.toTypedArray()
                mSequenceTriggerActions = sequenceTriggerActions.toTypedArray()
                mSequenceTriggerFlags = sequenceTriggerFlags.toIntArray()
                mSequenceTriggerOptions = sequenceTriggerOptions.toTypedArray()
                mSequenceTriggerConstraints = sequenceTriggerConstraints.toTypedArray()
                mSequenceTriggerConstraintMode = sequenceTriggerConstraintMode.toIntArray()
                mSequenceTriggersOverlappingSequenceTriggers =
                    sequenceTriggersOverlappingSequenceTriggers.map { it.toIntArray() }
                        .toTypedArray()

                mSequenceTriggersOverlappingParallelTriggers =
                    sequenceTriggersOverlappingParallelTriggers.map { it.toIntArray() }
                        .toTypedArray()

                mSequenceTriggerKeyFlags = sequenceTriggerKeyFlags.toTypedArray()

                mDetectParallelTriggers = parallelTriggerEvents.isNotEmpty()
                mParallelTriggerEvents = parallelTriggerEvents.toTypedArray()
                mParallelTriggerActions = parallelTriggerActions.toTypedArray()
                mParallelTriggerFlags = parallelTriggerFlags.toIntArray()
                mParallelTriggerOptions = parallelTriggerOptions.toTypedArray()
                mParallelTriggerConstraints = parallelTriggerConstraints.toTypedArray()
                mParallelTriggerConstraintMode = parallelTriggerConstraintMode.toIntArray()
                mParallelTriggerKeyFlags = parallelTriggerKeyFlags.toTypedArray()
                mParallelTriggerModifierKeyIndices =
                    parallelTriggerModifierKeyIndices.toTypedArray()
                this.mParallelTriggersOverlappingParallelTriggers =
                    parallelTriggersOverlappingParallelTriggers
                        .map { it.toIntArray() }
                        .toTypedArray()

                mDetectSequenceLongPresses = longPressSequenceEvents.isNotEmpty()
                mLongPressSequenceEvents = longPressSequenceEvents.toTypedArray()

                mDetectSequenceDoublePresses = doublePressEvents.isNotEmpty()
                mDoublePressEvents = doublePressEvents.toTypedArray()

                reset()
            }

            field = value
        }

    private var mDetectKeymaps = false
    private var mDetectInternalEvents = false
    private var mDetectExternalEvents = false
    private var mDetectSequenceTriggers = false
    private var mDetectSequenceLongPresses = false
    private var mDetectSequenceDoublePresses = false

    private var mDetectParallelTriggers = false

    /**
     * All sequence events that have the long press click type.
     */
    private var mLongPressSequenceEvents = arrayOf<Pair<Event, Int>>()

    /**
     * All double press sequence events and the index of their corresponding trigger. first is the event and second is
     * the trigger index.
     */
    private var mDoublePressEvents = arrayOf<TriggerKeyLocation>()

    /**
     * order matches with [mDoublePressEvents]
     */
    private var mDoublePressEventStates = intArrayOf()

    /**
     * The user has an amount of time to double press a key before it is registered as a double press.
     * The order matches with [mDoublePressEvents]. This array stores the time when the corresponding trigger will
     * timeout. If the key isn't waiting to timeout, the value is -1.
     */
    private var mDoublePressTimeoutTimes = longArrayOf()

    private var mActionMap = SparseArrayCompat<Action>()

    /**
     * A 2D array that stores the int values of options for each action in [mActionMap]
     */
    private var mActionOptions = arrayOf<IntArray>()

    /**
     * Stores the flags for each action in [mActionMap]
     */
    private var mActionFlags = intArrayOf()

    /**
     * The events to detect for each sequence trigger.
     */
    private var mSequenceTriggerEvents = arrayOf<Array<Event>>()

    /**
     * The flags for each key associated with the events in [mSequenceTriggerEvents]
     */
    private var mSequenceTriggerKeyFlags = arrayOf<IntArray>()

    private var mSequenceTriggerFlags = intArrayOf()

    /**
     * The actions to perform when each trigger is detected. The order matches with
     * [mSequenceTriggerEvents].
     */
    private var mSequenceTriggerActions = arrayOf<IntArray>()

    /**
     * Sequence triggers timeout after the first key has been pressed. The order matches with [mSequenceTriggerEvents].
     * This array stores the time when the corresponding trigger in will timeout. If the trigger in
     * isn't waiting to timeout, the value is -1.
     */
    private var mSequenceTriggersTimeoutTimes = longArrayOf()

    /**
     * The indexes of triggers that overlap after the first element with each trigger in [mSequenceTriggerEvents]
     */
    private var mSequenceTriggersOverlappingSequenceTriggers = arrayOf<IntArray>()

    private var mSequenceTriggersOverlappingParallelTriggers = arrayOf<IntArray>()

    /**
     * An array of the index of the last matched event in each sequence trigger.
     */
    private var mLastMatchedSequenceEventIndices = intArrayOf()

    /**
     * A 2D array that stores the int values of options for sequence triggers. If the trigger is set to
     * use the default value, the value is -1.
     */
    private var mSequenceTriggerOptions = arrayOf<IntArray>()

    private var mSequenceTriggerConstraints = arrayOf<Array<Constraint>>()
    private var mSequenceTriggerConstraintMode = intArrayOf()

    /**
     * The events to detect for each parallel trigger.
     */
    private var mParallelTriggerEvents = arrayOf<Array<Event>>()

    /**
     * The flags for each key associated with the events in [mParallelTriggerEvents]
     */
    private var mParallelTriggerKeyFlags = arrayOf<IntArray>()

    private var mParallelTriggerFlags = intArrayOf()

    /**
     * The actions to perform when each trigger is detected. The order matches with
     * [mParallelTriggerEvents].
     */
    private var mParallelTriggerActions = arrayOf<IntArray>()

    private var mParallelTriggerConstraints = arrayOf<Array<Constraint>>()
    private var mParallelTriggerConstraintMode = intArrayOf()

    /**
     * Stores whether each event in each parallel trigger need to be "released" after being held down.
     * The order matches with [mParallelTriggerEvents].
     */
    private var mParallelTriggerEventsAwaitingRelease = arrayOf<BooleanArray>()

    /**
     * An array of the index of the last matched event in each parallel trigger.
     */
    private var mLastMatchedParallelEventIndices = intArrayOf()

    /**
     * A 2D array which stores the int values of options for parallel triggers. If the trigger is set to
     * use the default value, the value is -1.
     */
    private var mParallelTriggerOptions = arrayOf<IntArray>()

    private var mParallelTriggerModifierKeyIndices = arrayOf<Pair<Int, Int>>()

    /**
     * The indexes of triggers that overlap after the first element with each trigger in [parallelTriggerEvents]
     */
    private var mParallelTriggersOverlappingParallelTriggers = arrayOf<IntArray>()

    private var mModifierKeyEventActions = false
    private var mNotModifierKeyEventActions = false
    private var keyCodesToImitateUpAction = mutableSetOf<Int>()
    private var mMetaStateFromActions = 0
    private var mMetaStateFromKeyEvent = 0

    private val mEventDownTimeMap = mutableMapOf<Event, Long>()

    /**
     * The indexes of parallel triggers that didn't have their actions performed because there is a matching trigger but
     * for a long-press. These actions should only be performed if the long-press fails, otherwise when the user
     * holds down the trigger keys for the long-press trigger, actions from both triggers will be performed.
     */
    private val mPerformActionsOnFailedLongPress = mutableSetOf<Int>()

    /**
     * The indexes of parallel triggers that didn't have their actions performed because there is a matching trigger but
     * for a double-press. These actions should only be performed if the double-press fails, otherwise each time the user
     * presses the keys for the double press, actions from both triggers will be performed.
     */
    private val mPerformActionsOnFailedDoublePress = mutableSetOf<Int>()

    /**
     * Maps repeat jobs to their corresponding parallel trigger index.
     */
    private val mRepeatJobs = SparseArrayCompat<List<RepeatJob>>()

    /**
     * Maps jobs to perform an action after a long press to their corresponding parallel trigger index
     */
    private val mParallelTriggerLongPressJobs = SparseArrayCompat<Job>()

    private val mParallelTriggerActionJobs = SparseArrayCompat<Job>()
    private val mSequenceTriggerActionJobs = SparseArrayCompat<Job>()

    /**
     * A list of all the action keys that are being held down.
     */
    private var mActionsBeingHeldDown = mutableSetOf<Int>()

    val performAction = LiveEvent<PerformAction>()
    val imitateButtonPress: LiveEvent<ImitateButtonPress> = LiveEvent()
    val vibrate: LiveEvent<Vibrate> = LiveEvent()

    /**
     * @return whether to consume the [KeyEvent].
     */
    fun onKeyEvent(
        keyCode: Int,
        action: Int,
        descriptor: String,
        isExternal: Boolean,
        metaState: Int,
        deviceId: Int,
        scanCode: Int = 0
    ): Boolean {
        if (!mDetectKeymaps) return false

        if ((isExternal && !mDetectExternalEvents) || (!isExternal && !mDetectInternalEvents)) {
            return false
        }

        mMetaStateFromKeyEvent = metaState

        //remove the metastate from any modifier keys that remapped and are pressed down
        mParallelTriggerModifierKeyIndices.forEach {
            val triggerIndex = it.first
            val eventIndex = it.second
            val event = mParallelTriggerEvents[triggerIndex][eventIndex]

            if (mParallelTriggerEventsAwaitingRelease[triggerIndex][eventIndex]) {
                mMetaStateFromKeyEvent =
                    mMetaStateFromKeyEvent.minusFlag(KeyEventUtils.modifierKeycodeToMetaState(event.keyCode))
            }
        }

        val event =
            if (isExternal) {
                Event(keyCode, Trigger.UNDETERMINED, descriptor)
            } else {
                Event(keyCode, Trigger.UNDETERMINED, Trigger.Key.DEVICE_ID_THIS_DEVICE)
            }

        when (action) {
            KeyEvent.ACTION_DOWN -> return onKeyDown(event, deviceId, scanCode)
            KeyEvent.ACTION_UP -> return onKeyUp(event, deviceId, scanCode)
        }

        return false
    }

    /**
     * @return whether to consume the [KeyEvent].
     */
    private fun onKeyDown(event: Event, deviceId: Int, scanCode: Int): Boolean {

        mEventDownTimeMap[event] = currentTime

        var consumeEvent = false
        val isModifierKeyCode = isModifierKey(event.keyCode)
        var mappedToParallelTriggerAction = false

        //consume sequence trigger keys until their timeout has been reached
        mSequenceTriggersTimeoutTimes.forEachIndexed { triggerIndex, timeoutTime ->
            if (!areSequenceTriggerConstraintsSatisfied(triggerIndex)) return@forEachIndexed

            if (timeoutTime != -1L && currentTime >= timeoutTime) {
                mLastMatchedSequenceEventIndices[triggerIndex] = -1
                mSequenceTriggersTimeoutTimes[triggerIndex] = -1
            } else {
                //consume the event if the trigger contains this keycode.
                mSequenceTriggerEvents[triggerIndex].forEachIndexed { eventIndex, sequenceEvent ->
                    if (sequenceEvent.keyCode == event.keyCode && mSequenceTriggerKeyFlags[triggerIndex][eventIndex].consume) {
                        consumeEvent = true
                    }
                }
            }
        }

        mDoublePressTimeoutTimes.forEachIndexed { doublePressEventIndex, timeoutTime ->
            if (currentTime >= timeoutTime) {
                mDoublePressTimeoutTimes[doublePressEventIndex] = -1
                mDoublePressEventStates[doublePressEventIndex] = NOT_PRESSED

            } else {
                val eventLocation = mDoublePressEvents[doublePressEventIndex]
                val doublePressEvent =
                    mSequenceTriggerEvents[eventLocation.triggerIndex][eventLocation.keyIndex]
                val triggerIndex = eventLocation.triggerIndex

                mSequenceTriggerEvents[triggerIndex].forEachIndexed { eventIndex, event ->
                    if (event == doublePressEvent
                        && mSequenceTriggerKeyFlags[triggerIndex][eventIndex].consume
                    ) {

                        consumeEvent = true
                    }
                }
            }
        }

        var awaitingLongPress = false
        var showPerformingActionToast = false
        val detectedShortPressTriggers = mutableSetOf<Int>()
        val vibrateDurations = mutableListOf<Long>()

        /* cache whether an action can be performed to avoid repeatedly checking when multiple triggers have the
        same action */
        val canActionBePerformed = SparseArrayCompat<Result<Action>>()

        if (mDetectParallelTriggers) {

            //only process keymaps if an action can be performed
            triggerLoop@ for ((triggerIndex, lastMatchedIndex) in mLastMatchedParallelEventIndices.withIndex()) {

                for (overlappingTriggerIndex in mSequenceTriggersOverlappingParallelTriggers[triggerIndex]) {
                    if (mLastMatchedSequenceEventIndices[overlappingTriggerIndex] != -1) {
                        continue@triggerLoop
                    }
                }

                for (overlappingTriggerIndex in mParallelTriggersOverlappingParallelTriggers[triggerIndex]) {
                    if (mLastMatchedParallelEventIndices[overlappingTriggerIndex] != -1) {
                        continue@triggerLoop
                    }
                }

                val constraints = mParallelTriggerConstraints[triggerIndex]
                val constraintMode = mParallelTriggerConstraintMode[triggerIndex]

                if (!constraints.constraintsSatisfied(constraintMode)) continue

                for (actionKey in mParallelTriggerActions[triggerIndex]) {
                    if (canActionBePerformed[actionKey] == null) {
                        val action = mActionMap[actionKey] ?: continue

                        val result = canActionBePerformed(action)
                        canActionBePerformed.put(actionKey, result)

                        if (result.isFailure) {
                            continue@triggerLoop
                        }
                    } else if (canActionBePerformed.get(actionKey, null) is Failure) {
                        continue@triggerLoop
                    }
                }

                val nextIndex = lastMatchedIndex + 1

                //Perform short press action

                if (mParallelTriggerEvents[triggerIndex].hasEventAtIndex(
                        event.withShortPress,
                        nextIndex
                    )
                ) {

                    if (mParallelTriggerKeyFlags[triggerIndex][nextIndex].consume) {
                        consumeEvent = true
                    }

                    mLastMatchedParallelEventIndices[triggerIndex] = nextIndex
                    mParallelTriggerEventsAwaitingRelease[triggerIndex][nextIndex] = true

                    if (nextIndex == mParallelTriggerEvents[triggerIndex].lastIndex) {
                        mappedToParallelTriggerAction = true

                        val actionKeys = mParallelTriggerActions[triggerIndex]

                        actionKeys.forEach { actionKey ->
                            val action = mActionMap[actionKey] ?: return@forEach

                            if (action.type == ActionType.KEY_EVENT) {
                                val actionKeyCode = action.data.toInt()

                                if (isModifierKey(actionKeyCode)) {
                                    val actionMetaState =
                                        KeyEventUtils.modifierKeycodeToMetaState(actionKeyCode)
                                    mMetaStateFromActions =
                                        mMetaStateFromActions.withFlag(actionMetaState)
                                }
                            }

                            if (showPerformingActionToast(actionKey)) {
                                showPerformingActionToast = true
                            }

                            detectedShortPressTriggers.add(triggerIndex)

                            val vibrateDuration = when {
                                mParallelTriggerFlags.vibrate(triggerIndex) -> {
                                    vibrateDuration(mParallelTriggerOptions[triggerIndex])
                                }

                                preferences.forceVibrate -> preferences.defaultVibrateDuration.toLong()
                                else -> -1L
                            }

                            vibrateDurations.add(vibrateDuration)
                        }
                    }
                }

                //Perform long press action
                if (mParallelTriggerEvents[triggerIndex].hasEventAtIndex(
                        event.withLongPress,
                        nextIndex
                    )
                ) {

                    if (mParallelTriggerKeyFlags[triggerIndex][nextIndex].consume) {
                        consumeEvent = true
                    }

                    mLastMatchedParallelEventIndices[triggerIndex] = nextIndex
                    mParallelTriggerEventsAwaitingRelease[triggerIndex][nextIndex] = true

                    if (nextIndex == mParallelTriggerEvents[triggerIndex].lastIndex) {
                        awaitingLongPress = true

                        if (mParallelTriggerFlags[triggerIndex]
                                .hasFlag(Trigger.TRIGGER_FLAG_LONG_PRESS_DOUBLE_VIBRATION)
                        ) {
                            vibrate.value =
                                Vibrate(vibrateDuration(mParallelTriggerOptions[triggerIndex]))
                        }

                        val oldJob = mParallelTriggerLongPressJobs[triggerIndex]
                        oldJob?.cancel()
                        mParallelTriggerLongPressJobs.put(
                            triggerIndex,
                            performActionsAfterLongPressDelay(triggerIndex)
                        )
                    }
                }
            }
        }

        if (mModifierKeyEventActions
            && !isModifierKeyCode
            && mMetaStateFromActions != 0
            && !mappedToParallelTriggerAction
        ) {

            consumeEvent = true
            keyCodesToImitateUpAction.add(event.keyCode)

            imitateButtonPress.value = ImitateButtonPress(
                event.keyCode,
                mMetaStateFromKeyEvent.withFlag(mMetaStateFromActions),
                deviceId,
                KeyEventAction.DOWN,
                scanCode
            )

            mCoroutineScope.launch {
                repeatImitatingKey(event.keyCode, deviceId, scanCode)
            }
        }

        if (detectedShortPressTriggers.isNotEmpty()) {
            val matchingDoublePressEvent = mDoublePressEvents.any {
                mSequenceTriggerEvents[it.triggerIndex][it.keyIndex].matchesEvent(event.withDoublePress)
            }

            /* to prevent the actions of keys mapped to a short press and, a long press or a double press
             * from crossing over.
             */
            when {
                matchingDoublePressEvent -> {
                    mPerformActionsOnFailedDoublePress.addAll(detectedShortPressTriggers)
                }

                awaitingLongPress -> {
                    mPerformActionsOnFailedLongPress.addAll(detectedShortPressTriggers)
                }

                else -> detectedShortPressTriggers.forEach { triggerIndex ->

                    mParallelTriggerActionJobs[triggerIndex]?.cancel()

                    mParallelTriggerActionJobs[triggerIndex] = mCoroutineScope.launch {

                        mParallelTriggerActions[triggerIndex].forEachIndexed { index, actionKey ->
                            val action = mActionMap[actionKey] ?: return@forEachIndexed

                            var shouldPerformActionNormally = true

                            if (action.holdDown && action.repeat
                                && stopRepeatingWhenPressedAgain(actionKey)
                            ) {

                                shouldPerformActionNormally = false

                                if (mActionsBeingHeldDown.contains(actionKey)) {
                                    mActionsBeingHeldDown.remove(actionKey)

                                    performAction(
                                        action,
                                        showPerformingActionToast(actionKey),
                                        keyEventAction = KeyEventAction.UP,
                                        multiplier = actionMultiplier(actionKey)
                                    )

                                } else {
                                    mActionsBeingHeldDown.add(actionKey)
                                }
                            }

                            if (holdDownUntilPressedAgain(actionKey)) {
                                if (mActionsBeingHeldDown.contains(actionKey)) {
                                    mActionsBeingHeldDown.remove(actionKey)

                                    performAction(
                                        action,
                                        showPerformingActionToast(actionKey),
                                        keyEventAction = KeyEventAction.UP,
                                        multiplier = actionMultiplier(actionKey)
                                    )

                                    shouldPerformActionNormally = false
                                }
                            }

                            if (shouldPerformActionNormally) {
                                if (action.holdDown) {
                                    mActionsBeingHeldDown.add(actionKey)
                                }

                                val keyEventAction =
                                    if (action.flags.hasFlag(Action.ACTION_FLAG_HOLD_DOWN)) {
                                        KeyEventAction.DOWN
                                    } else {
                                        KeyEventAction.DOWN_UP
                                    }

                                performAction(
                                    action,
                                    showPerformingActionToast,
                                    actionMultiplier(actionKey),
                                    keyEventAction
                                )

                                val vibrateDuration = vibrateDurations[index]

                                if (vibrateDuration != -1L) {
                                    vibrate.value = Vibrate(vibrateDuration)
                                }

                                if (action.repeat && action.holdDown) {
                                    delay(holdDownDuration(actionKey))

                                    performAction(
                                        action,
                                        false,
                                        1,
                                        KeyEventAction.UP
                                    )
                                }
                            }

                            delay(delayBeforeNextAction(actionKey))
                        }

                        initialiseRepeating(triggerIndex, calledOnTriggerRelease = false)
                    }
                }
            }
        }

        if (consumeEvent) {
            return true
        }

        if (mDetectSequenceTriggers) {
            mSequenceTriggerEvents.forEachIndexed { triggerIndex, events ->
                if (!areSequenceTriggerConstraintsSatisfied(triggerIndex)) return@forEachIndexed

                events.forEachIndexed { eventIndex, sequenceEvent ->
                    val matchingEvent = when {
                        sequenceEvent.matchesEvent(event.withShortPress) -> true
                        sequenceEvent.matchesEvent(event.withLongPress) -> true
                        sequenceEvent.matchesEvent(event.withDoublePress) -> true

                        else -> false
                    }

                    if (matchingEvent && mSequenceTriggerKeyFlags[triggerIndex][eventIndex].consume) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * @return whether to consume the event.
     */
    private fun onKeyUp(event: Event, deviceId: Int, scanCode: Int): Boolean {
        val keyCode = event.keyCode

        val downTime = mEventDownTimeMap[event] ?: currentTime
        mEventDownTimeMap.remove(event)

        var consumeEvent = false
        var imitateDownUpKeyEvent = false
        var imitateUpKeyEvent = false

        var successfulLongPress = false
        var successfulDoublePress = false
        var mappedToDoublePress = false
        var matchedDoublePressEventIndex = -1
        var shortPressSingleKeyTriggerJustReleased = false
        var longPressSingleKeyTriggerJustReleased = false

        var showPerformingActionToast = false

        val detectedSequenceTriggerIndexes = mutableListOf<Int>()
        val detectedParallelTriggerIndexes = mutableListOf<Int>()

        val vibrateDurations = mutableListOf<Long>()

        val imitateKeyAfterDoublePressTimeout = mutableListOf<Long>()

        var metaStateFromActionsToRemove = 0

        if (keyCodesToImitateUpAction.contains(keyCode)) {
            consumeEvent = true
            imitateUpKeyEvent = true
            keyCodesToImitateUpAction.remove(keyCode)
        }

        if (mDetectSequenceDoublePresses) {
            //iterate over each possible double press event to detect
            for (index in mDoublePressEvents.indices) {
                val eventLocation = mDoublePressEvents[index]
                val doublePressEvent =
                    mSequenceTriggerEvents[eventLocation.triggerIndex][eventLocation.keyIndex]
                val triggerIndex = eventLocation.triggerIndex

                if (!areSequenceTriggerConstraintsSatisfied(triggerIndex)) continue

                if (mLastMatchedSequenceEventIndices[triggerIndex] != eventLocation.keyIndex - 1) continue

                if (doublePressEvent.matchesEvent(event.withDoublePress)) {
                    mappedToDoublePress = true
                    //increment the double press event state.
                    mDoublePressEventStates[index] = mDoublePressEventStates[index] + 1

                    when (mDoublePressEventStates[index]) {
                        /*if the key is in the single pressed state, set the timeout time and start the timer
                        * to imitate the key if it isn't double pressed in the end */
                        SINGLE_PRESSED -> {

                            /*
                            I just realised that calculating the double press timeout is *SUPPOSED* to be in the onKeyDown
                            method but it has been this way for so long and no one has complained so leave it.
                             Changing this might affect people's key maps in ways that I can't fathom.
                             */

                            val doublePressTimeout =
                                doublePressTimeout(mSequenceTriggerOptions[triggerIndex])
                            mDoublePressTimeoutTimes[index] = currentTime + doublePressTimeout

                            imitateKeyAfterDoublePressTimeout.add(doublePressTimeout)
                            matchedDoublePressEventIndex = index

                            mSequenceTriggerEvents[triggerIndex].forEachIndexed { eventIndex, sequenceEvent ->
                                if (sequenceEvent == doublePressEvent
                                    && mSequenceTriggerKeyFlags[triggerIndex][eventIndex].consume
                                ) {

                                    consumeEvent = true
                                }
                            }
                        }

                        /* When the key is double pressed */
                        DOUBLE_PRESSED -> {

                            successfulDoublePress = true
                            mDoublePressEventStates[index] = NOT_PRESSED
                            mDoublePressTimeoutTimes[index] = -1
                        }
                    }
                }
            }
        }

        if (mDetectSequenceTriggers) {
            triggerLoop@ for ((triggerIndex, lastMatchedEventIndex) in mLastMatchedSequenceEventIndices.withIndex()) {
                if (!areSequenceTriggerConstraintsSatisfied(triggerIndex)) continue

                //the index of the next event to match in the trigger
                val nextIndex = lastMatchedEventIndex + 1

                if ((currentTime - downTime) >= longPressDelay(mSequenceTriggerOptions[triggerIndex])) {
                    successfulLongPress = true
                } else if (mDetectSequenceLongPresses &&
                    mLongPressSequenceEvents.any { it.first.matchesEvent(event.withLongPress) }
                ) {
                    imitateDownUpKeyEvent = true
                }

                val encodedEventWithClickType = when {
                    successfulLongPress -> event.withLongPress
                    successfulDoublePress -> event.withDoublePress
                    else -> event.withShortPress
                }

                for (overlappingTriggerIndex in mSequenceTriggersOverlappingSequenceTriggers[triggerIndex]) {
                    if (mLastMatchedSequenceEventIndices[overlappingTriggerIndex] != -1) {
                        continue@triggerLoop
                    }
                }

                //if the next event matches the event just pressed
                if (mSequenceTriggerEvents[triggerIndex].hasEventAtIndex(
                        encodedEventWithClickType,
                        nextIndex
                    )
                ) {

                    if (mSequenceTriggerKeyFlags[triggerIndex][nextIndex].consume) {
                        consumeEvent = true
                    }

                    mLastMatchedSequenceEventIndices[triggerIndex] = nextIndex

                    /*
                    If the next index is 0, then the first event in the trigger has been matched, which means the timer
                    needs to start for this trigger.
                     */
                    if (nextIndex == 0) {
                        val startTime = currentTime
                        val timeout = sequenceTriggerTimeout(mSequenceTriggerOptions[triggerIndex])

                        mSequenceTriggersTimeoutTimes[triggerIndex] = startTime + timeout
                    }

                    /*
                    If the last event in a trigger has been matched, then the action needs to be performed and the timer
                    reset.
                     */
                    if (nextIndex == mSequenceTriggerEvents[triggerIndex].lastIndex) {
                        detectedSequenceTriggerIndexes.add(triggerIndex)

                        mSequenceTriggerActions[triggerIndex].forEachIndexed { index, key ->

                            val vibrateDuration =
                                if (mSequenceTriggerFlags[triggerIndex].hasFlag(Trigger.TRIGGER_FLAG_VIBRATE)) {
                                    vibrateDuration(mSequenceTriggerOptions[triggerIndex])
                                } else {
                                    -1
                                }

                            val showToast = showPerformingActionToast(key)

                            if (showToast) {
                                showPerformingActionToast = true
                            }

                            vibrateDurations.add(index, vibrateDuration)
                        }

                        mLastMatchedSequenceEventIndices[triggerIndex] = -1
                        mSequenceTriggersTimeoutTimes[triggerIndex] = -1
                    }
                }
            }
        }

        if (mDetectParallelTriggers) {
            triggerLoop@ for ((triggerIndex, events) in mParallelTriggerEvents.withIndex()) {
                val singleKeyTrigger = mParallelTriggerEvents[triggerIndex].size == 1

                var lastHeldDownEventIndex = -1

                for (eventIndex in events.indices) {
                    val awaitingRelease =
                        mParallelTriggerEventsAwaitingRelease[triggerIndex][eventIndex]

                    //short press
                    if (awaitingRelease && events.hasEventAtIndex(
                            event.withShortPress,
                            eventIndex
                        )
                    ) {
                        if (singleKeyTrigger) {
                            shortPressSingleKeyTriggerJustReleased = true
                        }

                        if (mModifierKeyEventActions) {
                            val actionKeys = mParallelTriggerActions[triggerIndex]
                            actionKeys.forEach { actionKey ->

                                mActionMap[actionKey]?.let { action ->
                                    if (action.type == ActionType.KEY_EVENT) {
                                        val actionKeyCode = action.data.toInt()

                                        if (action.type == ActionType.KEY_EVENT && isModifierKey(
                                                actionKeyCode
                                            )
                                        ) {
                                            val actionMetaState =
                                                KeyEventUtils.modifierKeycodeToMetaState(
                                                    actionKeyCode
                                                )
                                            mMetaStateFromActions =
                                                mMetaStateFromActions.minusFlag(actionMetaState)
                                        }
                                    }
                                }
                            }
                        }

                        mParallelTriggerEventsAwaitingRelease[triggerIndex][eventIndex] = false

                        if (mParallelTriggerKeyFlags[triggerIndex][eventIndex].consume) {
                            consumeEvent = true
                        }
                    }

                    //long press
                    if (awaitingRelease && events.hasEventAtIndex(
                            event.withLongPress,
                            eventIndex
                        )
                    ) {

                        if ((currentTime - downTime) >= longPressDelay(mParallelTriggerOptions[triggerIndex])) {
                            successfulLongPress = true
                        }

                        mParallelTriggerEventsAwaitingRelease[triggerIndex][eventIndex] = false

                        mParallelTriggerLongPressJobs[triggerIndex]?.cancel()

                        if (mParallelTriggerKeyFlags[triggerIndex][eventIndex].consume) {
                            consumeEvent = true
                        }

                        val lastMatchedIndex = mLastMatchedParallelEventIndices[triggerIndex]

                        if (singleKeyTrigger && successfulLongPress) {
                            longPressSingleKeyTriggerJustReleased = true
                        }

                        if (!imitateDownUpKeyEvent) {
                            if (singleKeyTrigger && !successfulLongPress) {
                                imitateDownUpKeyEvent = true
                            } else if (lastMatchedIndex > -1 &&
                                lastMatchedIndex < mParallelTriggerEvents[triggerIndex].lastIndex
                            ) {
                                imitateDownUpKeyEvent = true
                            }
                        }
                    }

                    if (mParallelTriggerEventsAwaitingRelease[triggerIndex][eventIndex] &&
                        lastHeldDownEventIndex == eventIndex - 1
                    ) {

                        lastHeldDownEventIndex = eventIndex
                    }
                }

                mLastMatchedParallelEventIndices[triggerIndex] = lastHeldDownEventIndex
                mMetaStateFromActions =
                    mMetaStateFromActions.minusFlag(metaStateFromActionsToRemove)

                //cancel repeating action jobs for this trigger
                if (lastHeldDownEventIndex != mParallelTriggerEvents[triggerIndex].lastIndex) {
                    mRepeatJobs[triggerIndex]?.forEach {
                        if (!stopRepeatingWhenPressedAgain(it.actionKey)) {
                            it.cancel()
                        }
                    }

                    val actionKeys = mParallelTriggerActions[triggerIndex]

                    actionKeys.forEach { actionKey ->
                        val action = mActionMap[actionKey] ?: return@forEach

                        if (!mActionsBeingHeldDown.contains(actionKey)) return@forEach

                        if (action.flags.hasFlag(Action.ACTION_FLAG_HOLD_DOWN)
                            && !holdDownUntilPressedAgain(actionKey)
                        ) {

                            mActionsBeingHeldDown.remove(actionKey)

                            performAction(
                                action,
                                showPerformingActionToast,
                                actionMultiplier(actionKey),
                                KeyEventAction.UP
                            )
                        }
                    }
                }
            }
        }

        //perform actions on failed long press
        if (!successfulLongPress) {
            val iterator = mPerformActionsOnFailedLongPress.iterator()

            while (iterator.hasNext()) {
                val triggerIndex = iterator.next()

                /*
                The last event in the trigger
                */
                val lastEvent = mParallelTriggerEvents[triggerIndex].last()

                if (event.withShortPress.matchesEvent(lastEvent)) {
                    detectedParallelTriggerIndexes.add(triggerIndex)

                    mParallelTriggerActions[triggerIndex].forEachIndexed { index, key ->

                        val vibrateDuration =
                            if (mParallelTriggerFlags[index].hasFlag(Trigger.TRIGGER_FLAG_VIBRATE)) {
                                vibrateDuration(mParallelTriggerOptions[triggerIndex])
                            } else {
                                -1
                            }
                        vibrateDurations.add(index, vibrateDuration)

                        val showToast = showPerformingActionToast(key)

                        if (showToast) {
                            showPerformingActionToast = true
                        }
                    }
                }

                iterator.remove()
            }
        }

        detectedSequenceTriggerIndexes.forEach { triggerIndex ->
            mSequenceTriggerActionJobs[triggerIndex]?.cancel()

            mSequenceTriggerActionJobs[triggerIndex] = mCoroutineScope.launch {
                mSequenceTriggerActions[triggerIndex].forEachIndexed { index, actionKey ->

                    val action = mActionMap[actionKey] ?: return@forEachIndexed

                    performAction(action, showPerformingActionToast, actionMultiplier(actionKey))

                    if (vibrateDurations[index] != -1L || preferences.forceVibrate) {
                        vibrate.value = Vibrate(vibrateDurations[index])
                    }

                    delay(delayBeforeNextAction(actionKey))
                }
            }
        }

        detectedParallelTriggerIndexes.forEach { triggerIndex ->
            mParallelTriggerActionJobs[triggerIndex]?.cancel()

            mParallelTriggerActionJobs[triggerIndex] = mCoroutineScope.launch {
                mParallelTriggerActions[triggerIndex].forEachIndexed { index, actionKey ->

                    val action = mActionMap[actionKey] ?: return@forEachIndexed

                    performAction(action, showPerformingActionToast, actionMultiplier(actionKey))

                    if (vibrateDurations[index] != -1L || preferences.forceVibrate) {
                        vibrate.value = Vibrate(vibrateDurations[index])
                    }

                    delay(delayBeforeNextAction(actionKey))
                }
            }

            initialiseRepeating(triggerIndex, calledOnTriggerRelease = true)
        }

        if (imitateKeyAfterDoublePressTimeout.isNotEmpty()
            && detectedSequenceTriggerIndexes.isEmpty()
            && detectedParallelTriggerIndexes.isEmpty()
            && !longPressSingleKeyTriggerJustReleased
        ) {

            imitateKeyAfterDoublePressTimeout.forEach { timeout ->
                mCoroutineScope.launch {
                    delay(timeout)

                    /*
                    If no actions have just been performed and the key has still only been single pressed, imitate it.
                     */
                    if (mDoublePressEventStates[matchedDoublePressEventIndex] != SINGLE_PRESSED) {
                        return@launch
                    }

                    if (performActionsOnFailedDoublePress(event)) {
                        return@launch
                    }

                    this@KeymapDetectionDelegate.imitateButtonPress.value =
                        ImitateButtonPress(
                            keyCode,
                            keyEventAction = KeyEventAction.DOWN_UP,
                            scanCode = scanCode
                        )
                }
            }
        }
        //only imitate a key if an action isn't going to be performed
        else if ((imitateDownUpKeyEvent || imitateUpKeyEvent)
            && detectedSequenceTriggerIndexes.isEmpty()
            && detectedParallelTriggerIndexes.isEmpty()
            && !shortPressSingleKeyTriggerJustReleased
            && !mappedToDoublePress
        ) {

            val keyEventAction = if (imitateUpKeyEvent) {
                KeyEventAction.UP
            } else {
                KeyEventAction.DOWN_UP
            }

            this.imitateButtonPress.value = ImitateButtonPress(
                keyCode,
                mMetaStateFromKeyEvent.withFlag(mMetaStateFromActions),
                deviceId,
                keyEventAction,
                scanCode
            )

            keyCodesToImitateUpAction.remove(event.keyCode)
        }

        return consumeEvent
    }

    fun reset() {
        mDoublePressEventStates = IntArray(mDoublePressEvents.size) { NOT_PRESSED }
        mDoublePressTimeoutTimes = LongArray(mDoublePressEvents.size) { -1L }

        mSequenceTriggersTimeoutTimes = LongArray(mSequenceTriggerEvents.size) { -1 }
        mLastMatchedSequenceEventIndices = IntArray(mSequenceTriggerEvents.size) { -1 }

        mLastMatchedParallelEventIndices = IntArray(mParallelTriggerEvents.size) { -1 }
        mParallelTriggerEventsAwaitingRelease = Array(mParallelTriggerEvents.size) {
            BooleanArray(mParallelTriggerEvents[it].size) { false }
        }

        mPerformActionsOnFailedDoublePress.clear()
        mPerformActionsOnFailedLongPress.clear()

        mActionsBeingHeldDown.forEach {
            val action = mActionMap[it] ?: return@forEach

            performAction(
                action,
                showPerformingActionToast = false,
                multiplier = 1,
                keyEventAction = KeyEventAction.UP
            )
        }

        mActionsBeingHeldDown = mutableSetOf()

        mMetaStateFromActions = 0
        mMetaStateFromKeyEvent = 0
        keyCodesToImitateUpAction = mutableSetOf()

        mRepeatJobs.valueIterator().forEach {
            it.forEach { job ->
                job.cancel()
            }
        }

        mRepeatJobs.clear()

        mParallelTriggerLongPressJobs.valueIterator().forEach {
            it.cancel()
        }

        mParallelTriggerLongPressJobs.clear()

        mParallelTriggerActionJobs.valueIterator().forEach {
            it.cancel()
        }

        mParallelTriggerActionJobs.clear()

        mSequenceTriggerActionJobs.valueIterator().forEach {
            it.cancel()
        }

        mSequenceTriggerActionJobs.clear()
    }

    /**
     * @return whether any actions were performed.
     */
    private fun performActionsOnFailedDoublePress(event: Event): Boolean {
        var showPerformingActionToast = false
        val detectedTriggerIndexes = mutableListOf<Int>()
        val vibrateDurations = mutableListOf<Long>()

        mPerformActionsOnFailedDoublePress.forEach { triggerIndex ->
            if (event.withShortPress.matchesEvent(mParallelTriggerEvents[triggerIndex].last())) {

                detectedTriggerIndexes.add(triggerIndex)

                mParallelTriggerActions[triggerIndex].forEach { _ ->

                    if (showPerformingActionToast(triggerIndex)) {
                        showPerformingActionToast = true
                    }

                    val vibrateDuration =
                        if (mParallelTriggerFlags[triggerIndex].hasFlag(Trigger.TRIGGER_FLAG_VIBRATE)) {
                            vibrateDuration(mParallelTriggerOptions[triggerIndex])
                        } else {
                            -1
                        }

                    vibrateDurations.add(vibrateDuration)
                }
            }
        }

        mPerformActionsOnFailedDoublePress.clear()

        detectedTriggerIndexes.forEach { triggerIndex ->
            mParallelTriggerActionJobs[triggerIndex]?.cancel()

            mParallelTriggerActionJobs[triggerIndex] = mCoroutineScope.launch {
                mParallelTriggerActions[triggerIndex].forEachIndexed { index, actionKey ->

                    val action = mActionMap[actionKey] ?: return@forEachIndexed

                    performAction(action, showPerformingActionToast, actionMultiplier(actionKey))

                    if (vibrateDurations[index] != -1L || preferences.forceVibrate) {
                        vibrate.value = Vibrate(vibrateDurations[index])
                    }

                    delay(delayBeforeNextAction(actionKey))
                }
            }

            initialiseRepeating(triggerIndex, calledOnTriggerRelease = true)
        }

        return detectedTriggerIndexes.isNotEmpty()
    }

    private fun encodeActionList(actions: List<Action>): IntArray {
        return actions.map { getActionKey(it) }.toIntArray()
    }

    /**
     * @return the key for the action in [mActionMap]. Returns -1 if the [action] can't be found.
     */
    private fun getActionKey(action: Action): Int {
        mActionMap.keyIterator().forEach { key ->
            if (mActionMap[key] == action) {
                return key
            }
        }

        throw Exception("Action $action not in the action map!")
    }

    private suspend fun repeatImitatingKey(keyCode: Int, deviceId: Int, scanCode: Int) {
        delay(400)

        while (keyCodesToImitateUpAction.contains(keyCode)) {
            imitateButtonPress.postValue(
                ImitateButtonPress(
                    keyCode,
                    mMetaStateFromKeyEvent.withFlag(mMetaStateFromActions),
                    deviceId,
                    KeyEventAction.DOWN,
                    scanCode
                )
            ) //use down action because this is what Android does

            delay(50)
        }
    }

    private fun repeatAction(actionKey: Int) = RepeatJob(actionKey) {
        mCoroutineScope.launch {
            val repeat = mActionFlags[actionKey].hasFlag(Action.ACTION_FLAG_REPEAT)
            if (!repeat) return@launch

            delay(repeatDelay(actionKey))

            while (true) {
                mActionMap[actionKey]?.let { action ->

                    if (action.type == ActionType.KEY_EVENT) {
                        if (isModifierKey(action.data.toInt())) return@let
                    }

                    if (action.holdDown && action.repeat) {
                        val holdDownDuration = holdDownDuration(actionKey)

                        performAction(
                            action,
                            false,
                            actionMultiplier(actionKey),
                            KeyEventAction.DOWN
                        )
                        delay(holdDownDuration)
                        performAction(action, false, actionMultiplier(actionKey), KeyEventAction.UP)
                    } else {
                        performAction(action, false, actionMultiplier(actionKey))
                    }
                }

                delay(repeatRate(actionKey))
            }
        }
    }

    /**
     * For parallel triggers only.
     */
    private fun performActionsAfterLongPressDelay(triggerIndex: Int) = mCoroutineScope.launch {
        delay(longPressDelay(mParallelTriggerOptions[triggerIndex]))

        val actionKeys = mParallelTriggerActions[triggerIndex]

        mParallelTriggerActionJobs[triggerIndex]?.cancel()

        mParallelTriggerActionJobs[triggerIndex] = mCoroutineScope.launch {

            actionKeys.forEach { actionKey ->
                val action = mActionMap[actionKey] ?: return@forEach

                var performActionNormally = true

                if (holdDownUntilPressedAgain(actionKey)) {
                    if (mActionsBeingHeldDown.contains(actionKey)) {
                        mActionsBeingHeldDown.remove(actionKey)

                        performAction(
                            action,
                            showPerformingActionToast(actionKey),
                            keyEventAction = KeyEventAction.UP,
                            multiplier = actionMultiplier(actionKey)
                        )

                        performActionNormally = false
                    } else {
                        mActionsBeingHeldDown.add(actionKey)
                    }
                }

                if (performActionNormally) {

                    if (action.holdDown) {
                        mActionsBeingHeldDown.add(actionKey)
                    }

                    val keyEventAction =
                        if (action.flags.hasFlag(Action.ACTION_FLAG_HOLD_DOWN)) {
                            KeyEventAction.DOWN
                        } else {
                            KeyEventAction.DOWN_UP
                        }

                    performAction(
                        action,
                        showPerformingActionToast(actionKey),
                        actionMultiplier(actionKey),
                        keyEventAction
                    )

                    if (mParallelTriggerFlags.vibrate(triggerIndex) || preferences.forceVibrate
                        || mParallelTriggerFlags[triggerIndex].hasFlag(Trigger.TRIGGER_FLAG_LONG_PRESS_DOUBLE_VIBRATION)
                    ) {
                        vibrate.value =
                            Vibrate(vibrateDuration(mParallelTriggerOptions[triggerIndex]))
                    }
                }

                delay(delayBeforeNextAction(actionKey))
            }

            initialiseRepeating(triggerIndex, calledOnTriggerRelease = false)
        }
    }

    /**
     * For parallel triggers only.
     */
    private fun initialiseRepeating(triggerIndex: Int, calledOnTriggerRelease: Boolean) {
        val actionKeys = mParallelTriggerActions[triggerIndex]
        val actionKeysToStartRepeating = actionKeys.toMutableSet()

        mRepeatJobs[triggerIndex]?.forEach {
            if (stopRepeatingWhenPressedAgain(it.actionKey)) {
                actionKeysToStartRepeating.remove(it.actionKey)
            }

            it.cancel()
        }

        val repeatJobs = mutableListOf<RepeatJob>()

        actionKeys.forEach { key ->
            //only start repeating when a trigger is released if it is to repeat until pressed again
            if (!stopRepeatingWhenPressedAgain(key) && calledOnTriggerRelease) {
                actionKeysToStartRepeating.remove(key)
            }
        }

        actionKeysToStartRepeating.forEach {
            repeatJobs.add(repeatAction(it))
        }

        mRepeatJobs.put(triggerIndex, repeatJobs)
    }

    private val Int.anyDevice
        get() = this < 8192

    private val Int.keyCode
        get() = this and 1023

    private val IntArray.keyCodes: IntArray
        get() {
            val array = IntArray(size)

            forEachIndexed { index, key ->
                array[index] = key.keyCode
            }

            return array
        }

    private fun Array<Event>.hasEventAtIndex(event: Event, index: Int): Boolean {
        if (index >= size) return false

        val triggerEvent = this[index]

        return triggerEvent.matchesEvent(event)
    }

    private fun Event.matchesEvent(event: Event): Boolean {
        if (this.deviceId == Trigger.Key.DEVICE_ID_ANY_DEVICE
            || event.deviceId == Trigger.Key.DEVICE_ID_ANY_DEVICE
        ) {

            if (this.keyCode == event.keyCode && this.clickType == event.clickType) {
                return true
            }

        } else {
            if (this.keyCode == event.keyCode
                && this.deviceId == event.deviceId
                && this.clickType == event.clickType
            ) {
                return true
            }
        }

        return false
    }

    @MainThread
    private fun performAction(
        action: Action,
        showPerformingActionToast: Boolean,
        multiplier: Int,
        keyEventAction: KeyEventAction = KeyEventAction.DOWN_UP
    ) {
        val additionalMetaState = mMetaStateFromKeyEvent.withFlag(mMetaStateFromActions)

        repeat(multiplier) {
            performAction.value = PerformAction(
                action,
                showPerformingActionToast,
                additionalMetaState,
                keyEventAction
            )
        }
    }

    private fun setActionMapAndOptions(actions: Set<Action>) {
        var key = 0

        val map = SparseArrayCompat<Action>()
        val options = mutableListOf<IntArray>()
        val flags = mutableListOf<Int>()

        actions.forEach { action ->
            map.put(key, action)

            val optionValues = IntArray(ACTION_EXTRA_INDEX_MAP.size) { -1 }

            ACTION_EXTRA_INDEX_MAP.entries.forEach {
                val extraId = it.key
                val index = it.value

                action.extras.getData(extraId).onSuccess { value ->
                    optionValues[index] = value.toInt()
                }
            }

            flags.add(action.flags)
            options.add(optionValues)

            key++
        }

        mActionFlags = flags.toIntArray()
        mActionOptions = options.toTypedArray()
        mActionMap = map
    }

    private val Int.consume
        get() = !this.hasFlag(Trigger.Key.FLAG_DO_NOT_CONSUME_KEY_EVENT)

    private val Action.mappedToModifier
        get() = type == ActionType.KEY_EVENT && isModifierKey(data.toInt())

    private fun IntArray.vibrate(triggerIndex: Int) =
        this[triggerIndex].hasFlag(Trigger.TRIGGER_FLAG_VIBRATE)

    private fun stopRepeatingWhenPressedAgain(actionKey: Int) =
        mActionOptions.getOrNull(actionKey)
            ?.getOrNull(INDEX_STOP_REPEAT_BEHAVIOR) == Action.STOP_REPEAT_BEHAVIOUR_TRIGGER_PRESSED_AGAIN

    private fun holdDownUntilPressedAgain(actionKey: Int) =
        mActionOptions.getOrNull(actionKey)
            ?.getOrNull(INDEX_HOLD_DOWN_BEHAVIOR) == Action.STOP_HOLD_DOWN_BEHAVIOR_TRIGGER_PRESSED_AGAIN

    private fun showPerformingActionToast(actionKey: Int) =
        mActionFlags.getOrNull(actionKey)?.hasFlag(Action.ACTION_FLAG_SHOW_PERFORMING_ACTION_TOAST)
            ?: false

    private fun isModifierKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_SYM,
            KeyEvent.KEYCODE_NUM,
            KeyEvent.KEYCODE_FUNCTION -> true

            else -> false
        }
    }

    private fun actionMultiplier(actionKey: Int): Int {
        return if (mActionOptions[actionKey][INDEX_ACTION_MULTIPLIER] == -1) {
            1
        } else {
            mActionOptions[actionKey][INDEX_ACTION_MULTIPLIER]
        }
    }

    private fun repeatDelay(actionKey: Int): Long {
        return if (mActionOptions[actionKey][INDEX_ACTION_REPEAT_DELAY] == -1) {
            preferences.defaultRepeatDelay.toLong()
        } else {
            mActionOptions[actionKey][INDEX_ACTION_REPEAT_DELAY].toLong()
        }
    }

    private fun repeatRate(actionKey: Int): Long {
        return if (mActionOptions[actionKey][INDEX_ACTION_REPEAT_RATE] == -1) {
            preferences.defaultRepeatRate.toLong()
        } else {
            mActionOptions[actionKey][INDEX_ACTION_REPEAT_RATE].toLong()
        }
    }

    private fun longPressDelay(options: IntArray): Long {
        return if (options[INDEX_TRIGGER_LONG_PRESS_DELAY] == -1) {
            preferences.defaultLongPressDelay.toLong()
        } else {
            options[INDEX_TRIGGER_LONG_PRESS_DELAY].toLong()
        }
    }

    private fun doublePressTimeout(options: IntArray): Long {
        return if (options[INDEX_TRIGGER_DOUBLE_PRESS_DELAY] == -1) {
            preferences.defaultDoublePressDelay.toLong()
        } else {
            options[INDEX_TRIGGER_DOUBLE_PRESS_DELAY].toLong()
        }
    }

    private fun vibrateDuration(options: IntArray): Long {
        return if (options[INDEX_TRIGGER_VIBRATE_DURATION] == -1) {
            preferences.defaultVibrateDuration.toLong()
        } else {
            options[INDEX_TRIGGER_VIBRATE_DURATION].toLong()
        }
    }

    private fun sequenceTriggerTimeout(options: IntArray): Long {
        return if (options[INDEX_TRIGGER_SEQUENCE_TRIGGER_TIMEOUT] == -1) {
            preferences.defaultSequenceTriggerTimeout.toLong()
        } else {
            options[INDEX_TRIGGER_SEQUENCE_TRIGGER_TIMEOUT].toLong()
        }
    }

    private fun delayBeforeNextAction(actionKey: Int): Long {
        return if (mActionOptions[actionKey][INDEX_DELAY_BEFORE_NEXT_ACTION] == -1) {
            0
        } else {
            mActionOptions[actionKey][INDEX_DELAY_BEFORE_NEXT_ACTION].toLong()
        }
    }

    private fun holdDownDuration(actionKey: Int): Long {
        return if (mActionOptions[actionKey][INDEX_HOLD_DOWN_DURATION] == -1) {
            preferences.defaultHoldDownDuration.toLong()
        } else {
            mActionOptions[actionKey][INDEX_HOLD_DOWN_DURATION].toLong()
        }
    }

    private fun areSequenceTriggerConstraintsSatisfied(triggerIndex: Int): Boolean {
        val constraints = mSequenceTriggerConstraints[triggerIndex]
        val constraintMode = mSequenceTriggerConstraintMode[triggerIndex]

        return constraints.constraintsSatisfied(constraintMode)
    }

    private val Event.withShortPress: Event
        get() = copy(clickType = SHORT_PRESS)

    private val Event.withLongPress: Event
        get() = copy(clickType = LONG_PRESS)

    private val Event.withDoublePress: Event
        get() = copy(clickType = DOUBLE_PRESS)

    private data class Event(val keyCode: Int, val clickType: Int, val deviceId: String)
    private class RepeatJob(val actionKey: Int, launch: () -> Job) : Job by launch.invoke()
    private data class TriggerKeyLocation(val triggerIndex: Int, val keyIndex: Int)
}