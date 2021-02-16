package io.github.sds100.keymapper.data.viewmodel

import androidx.lifecycle.*
import com.hadilq.liveevent.LiveEvent
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.ChooseConstraintListItemModel
import io.github.sds100.keymapper.data.model.Constraint
import io.github.sds100.keymapper.data.model.ConstraintType
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.delegate.IModelState

/**
 * Created by sds100 on 21/03/2020.
 */

class ChooseConstraintListViewModel(
    private val supportedConstraints: List<String>
) : ViewModel(), IModelState<Map<Int, List<ChooseConstraintListItemModel>>> {

    companion object {
        private val ALL_MODELS = listOf(
            ChooseConstraintListItemModel(
                Constraint.APP_FOREGROUND,
                Constraint.CATEGORY_APP,
                R.string.constraint_choose_app_foreground),

            ChooseConstraintListItemModel(
                Constraint.APP_NOT_FOREGROUND,
                Constraint.CATEGORY_APP,
                R.string.constraint_choose_app_not_foreground
            ),

            ChooseConstraintListItemModel(
                Constraint.APP_PLAYING_MEDIA,
                Constraint.CATEGORY_APP,
                R.string.constraint_choose_app_playing_media
            ),

            ChooseConstraintListItemModel(
                Constraint.BT_DEVICE_CONNECTED,
                Constraint.CATEGORY_BLUETOOTH,
                R.string.constraint_choose_bluetooth_device_connected
            ),
            ChooseConstraintListItemModel(
                Constraint.BT_DEVICE_DISCONNECTED,
                Constraint.CATEGORY_BLUETOOTH,
                R.string.constraint_choose_bluetooth_device_disconnected
            ),

            ChooseConstraintListItemModel(
                Constraint.SCREEN_ON,
                Constraint.CATEGORY_SCREEN,
                R.string.constraint_choose_screen_on_description
            ),
            ChooseConstraintListItemModel(
                Constraint.SCREEN_OFF,
                Constraint.CATEGORY_SCREEN,
                R.string.constraint_choose_screen_off_description
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_PORTRAIT,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_portrait
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_LANDSCAPE,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_landscape
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_0,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_0
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_90,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_90
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_180,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_180
            ),
            ChooseConstraintListItemModel(
                Constraint.ORIENTATION_270,
                Constraint.CATEGORY_ORIENTATION,
                R.string.constraint_choose_orientation_270
            ),
        )

        private const val KEY_BT_CONSTRAINT_LIMITATION = "bt_constraint_limitation"
        private const val KEY_SCREEN_OFF_CONSTRAINT_LIMITATION = "bt_constraint_screen_off_limitation"
        private const val KEY_SCREEN_ON_CONSTRAINT_LIMITATION = "bt_constraint_screen_on_limitation"
    }

    override val model = liveData {
        emit(Loading())

        emit(
            sequence {
                for ((id, label) in Constraint.CATEGORY_LABEL_MAP) {
                    val constraints = ALL_MODELS.filter {
                        it.categoryId == id && supportedConstraints.contains(it.id)
                    }

                    if (constraints.isNotEmpty()) {
                        yield(label to constraints)
                    }
                }
            }.toMap().getState()
        )
    }
    override val viewState = MutableLiveData<ViewState>(ViewLoading())

    private val _eventStream = LiveEvent<Event>()
    val eventStream: LiveData<Event> = _eventStream

    private var chosenConstraintType: String? = null

    fun chooseConstraint(@ConstraintType constraintType: String) {
        chosenConstraintType = constraintType

        when (constraintType) {
            Constraint.APP_FOREGROUND,
            Constraint.APP_NOT_FOREGROUND,
            Constraint.APP_PLAYING_MEDIA,
            -> _eventStream.value = ChoosePackage()

            Constraint.BT_DEVICE_CONNECTED, Constraint.BT_DEVICE_DISCONNECTED -> {
                _eventStream.value = OkDialog(KEY_BT_CONSTRAINT_LIMITATION,
                    R.string.dialog_message_bt_constraint_limitation)
            }
            Constraint.SCREEN_ON -> {
                _eventStream.value = OkDialog(KEY_SCREEN_OFF_CONSTRAINT_LIMITATION,
                    R.string.dialog_message_screen_constraints_limitation)
            }
            Constraint.SCREEN_OFF -> {
                _eventStream.value = OkDialog(KEY_SCREEN_OFF_CONSTRAINT_LIMITATION,
                    R.string.dialog_message_screen_constraints_limitation)
            }
            else -> {
                _eventStream.value = SelectConstraint(Constraint(constraintType))
            }
        }
    }

    fun packageChosen(packageName: String) {
        _eventStream.value = SelectConstraint(Constraint.appConstraint(chosenConstraintType!!, packageName))
        chosenConstraintType = null
    }

    fun bluetoothDeviceChosen(address: String, name: String) {
        _eventStream.value = SelectConstraint(Constraint.btConstraint(chosenConstraintType!!, address, name))
        chosenConstraintType = null
    }


    fun onDialogResponse(key: String, response: UserResponse) {
        when (key) {
            KEY_BT_CONSTRAINT_LIMITATION ->
                _eventStream.value = ChooseBluetoothDevice()

            KEY_SCREEN_OFF_CONSTRAINT_LIMITATION ->
                _eventStream.value = SelectConstraint(Constraint(Constraint.SCREEN_OFF))

            KEY_SCREEN_ON_CONSTRAINT_LIMITATION ->
                _eventStream.value = SelectConstraint(Constraint(Constraint.SCREEN_ON))
        }
    }


    @Suppress("UNCHECKED_CAST")
    class Factory(private val supportedConstraints: List<String>) : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return ChooseConstraintListViewModel(supportedConstraints) as T
        }
    }
}