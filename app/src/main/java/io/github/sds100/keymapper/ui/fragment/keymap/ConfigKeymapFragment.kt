package io.github.sds100.keymapper.ui.fragment.keymap

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.options.KeymapActionOptions
import io.github.sds100.keymapper.data.model.options.TriggerKeyOptions
import io.github.sds100.keymapper.domain.constraints.Constraint
import io.github.sds100.keymapper.ui.fragment.*
import io.github.sds100.keymapper.ui.mappings.keymap.ConfigKeyMapViewModel
import io.github.sds100.keymapper.ui.showUserResponseRequests
import io.github.sds100.keymapper.ui.utils.getJsonSerializable
import io.github.sds100.keymapper.util.*

/**
 * Created by sds100 on 22/11/20.
 */
class ConfigKeymapFragment : ConfigMappingFragment() {

    private val args by navArgs<ConfigKeymapFragmentArgs>()

    override val viewModel: ConfigKeyMapViewModel by navGraphViewModels(R.id.nav_config_keymap) {
        InjectorUtils.provideConfigKeyMapViewModel(requireContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //only load the keymap if opening this fragment for the first time
        if (savedInstanceState == null) {
            args.keymapUid.let {
                if (it == null) {
                    viewModel.loadNewKeymap()
                } else {
                    viewModel.loadKeymap(it)
                }
            }
        }

        setFragmentResultListener(ConfigConstraintsFragment.CHOOSE_CONSTRAINT_REQUEST_KEY) { _, result ->
            result.getJsonSerializable<Constraint>(ChooseConstraintFragment.EXTRA_CONSTRAINT)?.let {
                viewModel.constraintListViewModel.onChosenNewConstraint(it)
            }
        }

        setFragmentResultListener(KeymapActionOptionsFragment.REQUEST_KEY) { _, result ->
            result.getParcelable<KeymapActionOptions>(BaseOptionsDialogFragment.EXTRA_OPTIONS)
                ?.let {
                    //TODO
                }
        }

        setFragmentResultListener(TriggerKeyOptionsFragment.REQUEST_KEY) { _, result ->
            result.getParcelable<TriggerKeyOptions>(BaseOptionsDialogFragment.EXTRA_OPTIONS)?.let {
                viewModel.triggerViewModel.setTriggerKeyOptions(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.triggerViewModel.showUserResponseRequests(this, binding)

        viewModel.triggerViewModel.optionsViewModel.showUserResponseRequests(this, binding)
    }

    override fun getFragmentInfoList() = intArray(R.array.config_keymap_fragments).map {
        when (it) {
            int(R.integer.fragment_id_trigger) -> it to TriggerFragment.Info()
            int(R.integer.fragment_id_trigger_options) -> it to TriggerOptionsFragment.Info()
            int(R.integer.fragment_id_constraint_list) -> it to KeymapConfigConstraintsFragment.Info()
            int(R.integer.fragment_id_action_list) -> it to KeymapActionListFragment.Info()

            int(R.integer.fragment_id_constraints_and_options) ->
                it to FragmentInfo(R.string.tab_constraints_and_more) {
                    ConstraintsAndOptionsFragment()
                }

            int(R.integer.fragment_id_trigger_and_action_list) ->
                it to FragmentInfo(R.string.tab_trigger_and_actions) { TriggerAndActionsFragment() }

            int(R.integer.fragment_id_config_keymap_all) ->
                it to FragmentInfo { AllFragments() }

            else -> throw Exception("Don't know how to create FragmentInfo for this fragment $it")
        }
    }

    class TriggerAndActionsFragment : TwoFragments(
        TriggerFragment.Info(),
        KeymapActionListFragment.Info()
    )

    class ConstraintsAndOptionsFragment : TwoFragments(
        TriggerOptionsFragment.Info(),
        KeymapConfigConstraintsFragment.Info()
    )

    class AllFragments : FourFragments(
        TriggerFragment.Info(),
        TriggerOptionsFragment.Info(),
        KeymapActionListFragment.Info(),
        KeymapConfigConstraintsFragment.Info()
    )
}