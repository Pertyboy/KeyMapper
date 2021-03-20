package io.github.sds100.keymapper.ui.fragment

import android.os.Bundle
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.github.sds100.keymapper.data.model.ChooseConstraintListItemModel
import io.github.sds100.keymapper.data.viewmodel.ChooseConstraintListViewModel
import io.github.sds100.keymapper.databinding.FragmentRecyclerviewBinding
import io.github.sds100.keymapper.sectionHeader
import io.github.sds100.keymapper.simple
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.delegate.ModelState
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.messageResource
import splitties.alertdialog.appcompat.okButton

/**
 * A placeholder fragment containing a simple view.
 */
class ChooseConstraintFragment
    : DefaultRecyclerViewFragment<Map<Int, List<ChooseConstraintListItemModel>>>() {

    companion object {
        const val EXTRA_CONSTRAINT = "extra_constraint"
    }

    private val navArgs by navArgs<ChooseConstraintFragmentArgs>()

    private val viewModel: ChooseConstraintListViewModel by viewModels {
        val supportedConstraints = navArgs.StringNavArgSupportedConstraintList
        InjectorUtils.provideChooseConstraintListViewModel(supportedConstraints.toList())
    }

    override val modelState: ModelState<Map<Int, List<ChooseConstraintListItemModel>>>
        get() = viewModel

    @Suppress("SuspiciousVarProperty")
    override var requestKey: String? = null
        get() = navArgs<ChooseConstraintFragmentArgs>().value.StringNavArgChooseConstraintRequestKey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(AppListFragment.REQUEST_KEY) { _, result ->
            val packageName = result.getString(AppListFragment.EXTRA_PACKAGE_NAME)

            viewModel.packageChosen(packageName!!)
        }

        setFragmentResultListener(BluetoothDeviceListFragment.REQUEST_KEY) { _, result ->
            val address = result.getString(BluetoothDeviceListFragment.EXTRA_ADDRESS)
            val name = result.getString(BluetoothDeviceListFragment.EXTRA_NAME)

            viewModel.bluetoothDeviceChosen(address!!, name!!)
        }
    }

    override fun subscribeUi(binding: FragmentRecyclerviewBinding) {
        super.subscribeUi(binding)

        viewModel.eventStream.observe(viewLifecycleOwner, { event ->
            when (event) {
                is ChoosePackage -> {
                    val direction = ChooseConstraintFragmentDirections
                        .actionChooseConstraintListFragmentToAppListFragment()
                    findNavController().navigate(direction)
                }

                is ChooseBluetoothDevice -> {
                    val direction =
                        ChooseConstraintFragmentDirections
                            .actionChooseConstraintListFragmentToBluetoothDevicesFragment()

                    findNavController().navigate(direction)
                }

                is OkDialog -> {
                    requireContext().alertDialog {
                        messageResource = event.message

                        okButton {
                            viewModel.onDialogResponse(event.responseKey, UserResponse.POSITIVE)
                        }

                        show()
                    }
                }

                is SelectConstraint -> {
                    returnResult(EXTRA_CONSTRAINT to event.constraint)
                }
            }
        })
    }

    override fun populateList(
        binding: FragmentRecyclerviewBinding,
        model: Map<Int, List<ChooseConstraintListItemModel>>?
    ) {
        binding.epoxyRecyclerView.withModels {
            for ((sectionHeader, constraints) in model ?: emptyMap()) {

                sectionHeader {
                    id(sectionHeader)
                    header(requireContext().str(sectionHeader))
                }

                constraints.forEach { constraint ->
                    simple {
                        id(constraint.id)
                        primaryText(requireContext().str(constraint.description))
                        isSecondaryTextAnError(true)

                        //TODO
                        val isSupported = false

//                        if (isSupported == null) {
//                            secondaryText(null)
//                        } else {
//                            secondaryText(isSupported.getFullMessage(requireContext()))
//                        }

                        onClick { _ ->
                            viewModel.chooseConstraint(constraint.id)
                        }
                    }
                }
            }
        }
    }
}