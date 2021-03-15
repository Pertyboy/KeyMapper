package io.github.sds100.keymapper.ui.fragment.fingerprint

import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.options.FingerprintActionOptions
import io.github.sds100.keymapper.data.viewmodel.ActionListViewModel
import io.github.sds100.keymapper.domain.mappings.fingerprintmap.FingerprintMapAction
import io.github.sds100.keymapper.ui.fragment.ActionListFragment
import io.github.sds100.keymapper.ui.mappings.fingerprintmap.ConfigFingerprintMapViewModel
import io.github.sds100.keymapper.util.FragmentInfo
import io.github.sds100.keymapper.util.InjectorUtils

/**
 * Created by sds100 on 22/11/20.
 */

class FingerprintActionListFragment : ActionListFragment<FingerprintActionOptions, FingerprintMapAction>() {

    class Info : FragmentInfo(
        R.string.action_list_header,
        R.string.url_action_guide,
        { FingerprintActionListFragment() }
    )

    override var isAppBarVisible = false

    private val viewModel: ConfigFingerprintMapViewModel
        by navGraphViewModels(R.id.nav_config_fingerprint_map) {
            InjectorUtils.provideFingerprintMapListViewModel(requireContext())
        }

    override val actionListViewModel: ActionListViewModel<FingerprintMapAction>
        get() = viewModel.actionListViewModel

    override fun openActionOptionsFragment(options: FingerprintActionOptions) {
        val direction = ConfigFingerprintMapFragmentDirections
            .actionConfigFingerprintMapFragmentToActionOptionsFragment(options)

        findNavController().navigate(direction)
    }
}