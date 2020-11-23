package io.github.sds100.keymapper.ui.fragment

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyTouchHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.TriggerKeyBindingModel_
import io.github.sds100.keymapper.data.model.Trigger
import io.github.sds100.keymapper.data.model.TriggerKeyModel
import io.github.sds100.keymapper.data.viewmodel.ConfigKeymapViewModel
import io.github.sds100.keymapper.databinding.FragmentTriggerBinding
import io.github.sds100.keymapper.service.MyAccessibilityService
import io.github.sds100.keymapper.triggerKey
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.alertdialog.appcompat.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Created by sds100 on 19/03/2020.
 */
class TriggerFragment(private val mKeymapId: Long) : Fragment() {
    private val mViewModel: ConfigKeymapViewModel by navGraphViewModels(R.id.nav_config_keymap) {
        InjectorUtils.provideConfigKeymapViewModel(requireContext(), mKeymapId)
    }

    private lateinit var mBinding: FragmentTriggerBinding

    /**
     * Listens for key events from the accessibility service
     */
    private val mBroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {

                Intent.ACTION_INPUT_METHOD_CHANGED -> {
                    mViewModel.rebuildActionModels()
                }

                MyAccessibilityService.ACTION_RECORDED_TRIGGER_KEY -> {
                    intent.getParcelableExtra<KeyEvent>(MyAccessibilityService.EXTRA_KEY_EVENT)?.let { keyEvent ->
                        if (!mSuccessfullyRecordedTrigger) {
                            mSuccessfullyRecordedTrigger = true
                        }

                        lifecycleScope.launch {
                            val deviceName = keyEvent.device.name
                            val deviceDescriptor = keyEvent.device.descriptor
                            val isExternal = keyEvent.device.isExternalCompat

                            mViewModel.addTriggerKey(keyEvent.keyCode, deviceDescriptor, deviceName, isExternal)
                        }
                    }
                }

                MyAccessibilityService.ACTION_RECORD_TRIGGER_TIMER_INCREMENTED -> {
                    val timeLeft = intent.getIntExtra(MyAccessibilityService.EXTRA_TIME_LEFT, -1)

                    if (timeLeft != -1) {
                        mViewModel.recordingTrigger.value = true
                        mViewModel.recordTriggerTimeLeft.value = timeLeft
                    }
                }

                MyAccessibilityService.ACTION_STOPPED_RECORDING_TRIGGER -> {
                    val stoppedEarly = mViewModel.recordTriggerTimeLeft.value?.let { it > 1 } ?: true

                    if (!stoppedEarly) {
                        mRecordingTriggerCount++
                    }

                    mViewModel.recordingTrigger.value = false

                    if (mRecordingTriggerCount >= 2 && !mSuccessfullyRecordedTrigger) {
                        requireContext().alertDialog {
                            titleResource = R.string.dialog_title_cant_record_trigger
                            messageResource = R.string.dialog_message_cant_record_trigger

                            okButton()

                            show()
                        }
                    }
                }
            }
        }
    }

    /**
     * The number of times the user has attempted to record a trigger.
     */
    private var mRecordingTriggerCount = 0

    /**
     * Whether the user has successfully recorded a trigger.
     */
    private var mSuccessfullyRecordedTrigger = false

    private val mTriggerKeyController = TriggerKeyController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        IntentFilter().apply {
            addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            addAction(MyAccessibilityService.ACTION_RECORD_TRIGGER_TIMER_INCREMENTED)
            addAction(MyAccessibilityService.ACTION_RECORDED_TRIGGER_KEY)
            addAction(MyAccessibilityService.ACTION_STOPPED_RECORDING_TRIGGER)

            requireActivity().registerReceiver(mBroadcastReceiver, this)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        FragmentTriggerBinding.inflate(inflater, container, false).apply {
            mBinding = this

            return this.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mBinding.apply {
            viewModel = mViewModel
            lifecycleOwner = viewLifecycleOwner

            subscribeTriggerList()

            epoxyRecyclerViewTriggers.adapter = mTriggerKeyController.adapter

            mViewModel.triggerMode.observe(viewLifecycleOwner) {
                mTriggerKeyController.requestModelBuild()
            }

            mViewModel.buildTriggerKeyModelListEvent.observe(viewLifecycleOwner, EventObserver { triggerKeys ->
                lifecycleScope.launch {
                    withContext(lifecycleScope.coroutineContext + Dispatchers.Default) {
                        val deviceInfoList = mViewModel.getDeviceInfoList()

                        val modelList = sequence {
                            triggerKeys.forEach {
                                val model = it.buildModel(requireContext(), deviceInfoList)
                                yield(model)
                            }
                        }.toList()

                        mViewModel.triggerKeyModelList.postValue(modelList)
                    }
                }
            })

            mViewModel.editTriggerKeyBehavior.observe(viewLifecycleOwner, EventObserver {
                val direction = ConfigKeymapFragmentDirections.actionConfigKeymapFragmentToTriggerKeyBehaviorFragment(it)

                findNavController().navigate(direction)
            })

            radioButtonShortPress.setOnClickListener { view ->
                if ((view as MaterialRadioButton).isChecked) {
                    mViewModel.setParallelTriggerClickType(Trigger.SHORT_PRESS)
                }
            }

            radioButtonLongPress.setOnClickListener { view ->
                if ((view as MaterialRadioButton).isChecked) {
                    mViewModel.setParallelTriggerClickType(Trigger.LONG_PRESS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mViewModel.rebuildActionModels()
    }

    override fun onDestroy() {
        super.onDestroy()

        requireActivity().unregisterReceiver(mBroadcastReceiver)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun FragmentTriggerBinding.subscribeTriggerList() {
        mViewModel.triggerKeyModelList.observe(viewLifecycleOwner, { triggerKeyList ->

            enableTriggerKeyDragging(mTriggerKeyController)
            mTriggerKeyController.modelList = triggerKeyList
        })
    }

    private suspend fun showChooseDeviceDialog() = suspendCoroutine<String> {
        requireActivity().alertDialog {

            val deviceIds = sequence {
                yield(Trigger.Key.DEVICE_ID_THIS_DEVICE)
                yield(Trigger.Key.DEVICE_ID_ANY_DEVICE)

                yieldAll(InputDeviceUtils.getExternalDeviceDescriptors())

            }.toList()

            val deviceLabels = sequence {
                yield(str(R.string.this_device))
                yield(str(R.string.any_device))

                yieldAll(InputDeviceUtils.getExternalDeviceNames())

            }.toList().toTypedArray()

            setItems(deviceLabels) { _, index ->
                val deviceId = deviceIds[index]

                it.resume(deviceId)
            }

            cancelButton()
            show()
        }
    }

    private fun FragmentTriggerBinding.enableTriggerKeyDragging(controller: EpoxyController): ItemTouchHelper {
        return EpoxyTouchHelper.initDragging(controller)
            .withRecyclerView(epoxyRecyclerViewTriggers)
            .forVerticalList()
            .withTarget(TriggerKeyBindingModel_::class.java)
            .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<TriggerKeyBindingModel_>() {

                override fun isDragEnabledForModel(model: TriggerKeyBindingModel_?): Boolean {
                    return mViewModel.triggerKeys.value?.size!! > 1
                }

                override fun onModelMoved(
                    fromPosition: Int,
                    toPosition: Int,
                    modelBeingMoved: TriggerKeyBindingModel_?,
                    itemView: View?
                ) {
                    mViewModel.moveTriggerKey(fromPosition, toPosition)
                }

                override fun onDragStarted(
                    model: TriggerKeyBindingModel_?,
                    itemView: View?,
                    adapterPosition: Int
                ) {
                    itemView?.findViewById<MaterialCardView>(R.id.cardView)?.isDragged = true
                }

                override fun onDragReleased(model: TriggerKeyBindingModel_?, itemView: View?) {
                    itemView?.findViewById<MaterialCardView>(R.id.cardView)?.isDragged = false
                }
            })
    }

    private inner class TriggerKeyController : EpoxyController() {
        var modelList: List<TriggerKeyModel> = listOf()
            set(value) {
                requestModelBuild()
                field = value
            }

        override fun buildModels() {
            modelList.forEachIndexed { index, model ->
                triggerKey {
                    id(model.id)
                    model(model)

                    triggerMode(mViewModel.triggerMode.value)
                    triggerKeyCount(modelList.size)
                    triggerKeyIndex(index)

                    onRemoveClick { _ ->
                        mViewModel.removeTriggerKey(model.keyCode)
                    }

                    onMoreClick { _ ->
                        mViewModel.editTriggerKeyBehavior(model.id)
                    }

                    onDeviceClick { _ ->
                        lifecycleScope.launch {
                            val deviceId = showChooseDeviceDialog()

                            mViewModel.setTriggerKeyDevice(model.keyCode, deviceId)
                        }
                    }
                }
            }
        }
    }
}