package io.github.sds100.keymapper.ui.fragment.fingerprint

import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.options.FingerprintMapOptions
import io.github.sds100.keymapper.data.model.options.OptionsListModel
import io.github.sds100.keymapper.data.viewmodel.BaseOptionsViewModel
import io.github.sds100.keymapper.databinding.FragmentRecyclerviewBinding
import io.github.sds100.keymapper.ui.fragment.DefaultRecyclerViewFragment
import io.github.sds100.keymapper.util.FragmentInfo
import io.github.sds100.keymapper.util.delegate.ModelState

/**
 * Created by sds100 on 29/11/20.
 */
class FingerprintMapOptionsFragment : DefaultRecyclerViewFragment<OptionsListModel>() {

    class Info : FragmentInfo(
        R.string.option_list_header,
        R.string.url_fingerprint_map_options_guide,
        { FingerprintMapOptionsFragment() }
    )

    val optionsViewModel: BaseOptionsViewModel<FingerprintMapOptions> = TODO()

    override val modelState: ModelState<OptionsListModel>
        get() = optionsViewModel

    override var isAppBarVisible = false


    override fun populateList(
        binding: FragmentRecyclerviewBinding,
        model: OptionsListModel?
    ) {
        //TODO
    }
}