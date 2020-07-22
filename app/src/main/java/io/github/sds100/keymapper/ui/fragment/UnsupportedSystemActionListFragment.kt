package io.github.sds100.keymapper.ui.fragment

import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import io.github.sds100.keymapper.data.viewmodel.SystemActionListViewModel
import io.github.sds100.keymapper.databinding.FragmentRecyclerviewBinding
import io.github.sds100.keymapper.simple
import io.github.sds100.keymapper.ui.callback.ProgressCallback
import io.github.sds100.keymapper.util.InjectorUtils
import io.github.sds100.keymapper.util.TintType
import io.github.sds100.keymapper.util.drawable
import io.github.sds100.keymapper.util.result.getFullMessage
import io.github.sds100.keymapper.util.str

/**
 * Created by sds100 on 31/03/2020.
 */
class UnsupportedSystemActionListFragment : RecyclerViewFragment() {

    private val mViewModel: SystemActionListViewModel by activityViewModels {
        InjectorUtils.provideSystemActionListViewModel(requireContext())
    }

    override val progressCallback: ProgressCallback?
        get() = mViewModel

    override fun subscribeList(binding: FragmentRecyclerviewBinding) {
        binding.apply {
            mViewModel.unsupportedSystemActions.observe(viewLifecycleOwner) { unsupportedActions ->
                epoxyRecyclerView.withModels {
                    unsupportedActions.forEach {
                        simple {
                            id(it.id)
                            icon(drawable(it.icon))
                            tintType(TintType.ON_SURFACE)
                            primaryText(str(it.description))
                            secondaryText(it.reason.getFullMessage(requireContext()))
                        }
                    }
                }
            }
        }
    }
}