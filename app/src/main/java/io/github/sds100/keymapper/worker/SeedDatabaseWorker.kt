package io.github.sds100.keymapper.worker

import android.content.Context
import android.view.KeyEvent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.ServiceLocator
import io.github.sds100.keymapper.data.model.ActionEntity
import io.github.sds100.keymapper.data.model.ConstraintEntity
import io.github.sds100.keymapper.data.model.KeyMapEntity
import io.github.sds100.keymapper.data.model.TriggerEntity
import io.github.sds100.keymapper.util.ActionType
import kotlinx.coroutines.coroutineScope

/**
 * Created by sds100 on 26/01/2020.
 */

class SeedDatabaseWorker(
    context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = coroutineScope {
        try {
            val keymaps = sequence {
                for (i in 1..100) {
                    yield(KeyMapEntity(
                        id = 0,
                        trigger = createRandomTrigger(),
                        actionList = createRandomActionList(),
                        constraintList = listOf(
                            ConstraintEntity.appConstraint(ConstraintEntity.APP_FOREGROUND, Constants.PACKAGE_NAME),
                            ConstraintEntity.appConstraint(ConstraintEntity.APP_NOT_FOREGROUND, "io.github.sds100.keymapper.ci")
                        ),
                        flags = 0
                    ))
                }
            }.toList().toTypedArray()

            ServiceLocator.keymapRepository(applicationContext).insertKeymap(*keymaps)

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun createRandomTrigger(): TriggerEntity {
        val keys = sequence {
            yield(TriggerEntity.KeyEntity(
                KeyEvent.KEYCODE_CTRL_LEFT,
                TriggerEntity.KeyEntity.DEVICE_ID_THIS_DEVICE,
                TriggerEntity.SHORT_PRESS
            ))
            yield(TriggerEntity.KeyEntity(
                KeyEvent.KEYCODE_ALT_LEFT,
                TriggerEntity.KeyEntity.DEVICE_ID_ANY_DEVICE,
                TriggerEntity.LONG_PRESS
            ))
            yield(TriggerEntity.KeyEntity(
                KeyEvent.KEYCODE_DEL,
                TriggerEntity.KeyEntity.DEVICE_ID_THIS_DEVICE,
                TriggerEntity.SHORT_PRESS
            ))
        }.toList()

        return TriggerEntity(keys, mode = TriggerEntity.SEQUENCE, flags = TriggerEntity.TRIGGER_FLAG_VIBRATE)
    }

    private fun createRandomActionList(): List<ActionEntity> {
        return sequence {
            yield(ActionEntity(
                type = ActionType.APP,
                data = Constants.PACKAGE_NAME
            ))
            yield(ActionEntity(
                type = ActionType.APP,
                data = "this.app.doesnt.exist"
            ))
        }.toList()
    }
}