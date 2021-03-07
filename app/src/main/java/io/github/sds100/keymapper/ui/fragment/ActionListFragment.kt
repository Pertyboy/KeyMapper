package io.github.sds100.keymapper.ui.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import com.airbnb.epoxy.EpoxyController
import com.airbnb.epoxy.EpoxyTouchHelper
import com.google.android.material.card.MaterialCardView
import io.github.sds100.keymapper.ActionBindingModel_
import io.github.sds100.keymapper.NavAppDirections
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.action
import io.github.sds100.keymapper.data.model.Action
import io.github.sds100.keymapper.data.model.ActionModel
import io.github.sds100.keymapper.data.model.options.BaseOptions
import io.github.sds100.keymapper.data.viewmodel.ActionListViewModel
import io.github.sds100.keymapper.databinding.FragmentActionListBinding
import io.github.sds100.keymapper.service.MyAccessibilityService
import io.github.sds100.keymapper.util.*

/**
 * Created by sds100 on 22/11/20.
 */
abstract class ActionListFragment<O : BaseOptions<Action>> : Fragment() {

    companion object {
        const val CHOOSE_ACTION_REQUEST_KEY = "request_choose_action"
    }

    abstract val actionListViewModel: ActionListViewModel<O>

    private val mActionListController = ActionListController()

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                Intent.ACTION_INPUT_METHOD_CHANGED -> {
                    actionListViewModel.rebuildModels()
                }
            }
        }
    }

    /**
     * Scoped to the lifecycle of the fragment's view (between onCreateView and onDestroyView)
     */
    private var _binding: FragmentActionListBinding? = null
    val binding: FragmentActionListBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        IntentFilter().apply {
            addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            requireContext().registerReceiver(mBroadcastReceiver, this)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        FragmentActionListBinding.inflate(inflater, container, false).apply {
            viewModel = actionListViewModel
            lifecycleOwner = viewLifecycleOwner

            _binding = this

            return this.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            subscribeActionList()

            epoxyRecyclerViewActions.adapter = mActionListController.adapter

            actionListViewModel.eventStream.observe(viewLifecycleOwner, { event ->
                @Suppress("UNCHECKED_CAST")
                when (event) {
                    is TestAction -> {
                        if (AccessibilityUtils.isServiceEnabled(requireContext())) {

                            requireContext().sendPackageBroadcast(MyAccessibilityService.ACTION_TEST_ACTION,
                                bundleOf(MyAccessibilityService.EXTRA_ACTION to event.action))

                        } else {
                            actionListViewModel.promptToEnableAccessibilityService()
                        }
                    }

                    is EditActionOptions -> openActionOptionsFragment(event.options as O)

                    is BuildActionListModels -> {
                        viewLifecycleScope.launchWhenStarted {
                            val deviceInfoList = actionListViewModel.getDeviceInfoList()

                            val models = sequence {
                                event.source.forEach {
                                    yield(it.buildModel(requireContext(), deviceInfoList))
                                }
                            }.toList()

                            actionListViewModel.setModels(models)
                        }
                    }
                }
            })

            actionListViewModel.modelList.observe(viewLifecycleOwner, {
                mActionListController.modelList = when (it) {
                    is Data -> it.data
                    else -> emptyList()
                }
            })

            setOnAddActionClick {
                val direction = NavAppDirections.actionGlobalChooseActionFragment(CHOOSE_ACTION_REQUEST_KEY)
                findNavController().navigate(direction)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        actionListViewModel.saveState(outState)

        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()

        actionListViewModel.rebuildModels()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        savedInstanceState ?: return

        actionListViewModel.restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    abstract fun openActionOptionsFragment(options: O)

    private fun FragmentActionListBinding.subscribeActionList() {
        actionListViewModel.modelList.observe(viewLifecycleOwner, { actionList ->
            enableActionDragging(mActionListController)

            actionList.ifIsData {
                mActionListController.modelList = it
            }
        })
    }

    private fun FragmentActionListBinding.enableActionDragging(controller: EpoxyController): ItemTouchHelper {
        return EpoxyTouchHelper.initDragging(controller)
            .withRecyclerView(epoxyRecyclerViewActions)
            .forVerticalList()
            .withTarget(ActionBindingModel_::class.java)
            .andCallbacks(object : EpoxyTouchHelper.DragCallbacks<ActionBindingModel_>() {

                override fun isDragEnabledForModel(model: ActionBindingModel_?): Boolean {
                    actionListViewModel.modelList.value?.ifIsData {
                        if (it.size > 1) return true
                    }

                    return false
                }

                override fun onModelMoved(
                    fromPosition: Int,
                    toPosition: Int,
                    modelBeingMoved: ActionBindingModel_?,
                    itemView: View?
                ) {
                    actionListViewModel.moveAction(fromPosition, toPosition)
                }

                override fun onDragStarted(
                    model: ActionBindingModel_?,
                    itemView: View?,
                    adapterPosition: Int
                ) {
                    itemView?.findViewById<MaterialCardView>(R.id.cardView)?.isDragged = true
                }

                override fun onDragReleased(model: ActionBindingModel_?, itemView: View?) {
                    itemView?.findViewById<MaterialCardView>(R.id.cardView)?.isDragged = false
                }
            })
    }

    private inner class ActionListController : EpoxyController() {
        var modelList: List<ActionModel> = listOf()
            set(value) {
                field = value
                requestModelBuild()
            }

        override fun buildModels() {
            modelList.forEach { model ->
                action {
                    id(model.id)
                    model(model)
                    actionCount(modelList.size)

                    onRemoveClick { _ ->
                        actionListViewModel.removeAction(model.id)
                    }

                    onMoreClick { _ ->
                        actionListViewModel.editOptions(model.id)
                    }

                    onClick { _ ->
                        actionListViewModel.onModelClick(model.id)
                    }
                }
            }
        }
    }
}