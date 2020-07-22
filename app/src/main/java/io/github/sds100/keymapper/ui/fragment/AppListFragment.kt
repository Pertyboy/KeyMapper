package io.github.sds100.keymapper.ui.fragment

import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import io.github.sds100.keymapper.data.viewmodel.AppListViewModel
import io.github.sds100.keymapper.databinding.FragmentRecyclerviewBinding
import io.github.sds100.keymapper.simple
import io.github.sds100.keymapper.util.InjectorUtils

/**
 * Created by sds100 on 22/02/2020.
 */
class AppListFragment : RecyclerViewFragment() {

    companion object {
        const val REQUEST_KEY = "request_app"
        const val EXTRA_APP_MODEL = "extra_app"
        const val SEARCH_STATE_KEY = "key_app_search_state"
    }

    override val progressCallback
        get() = mViewModel

    override var searchStateKey: String? = SEARCH_STATE_KEY
    override var resultData: ResultData? = ResultData(REQUEST_KEY, EXTRA_APP_MODEL)

    private val mViewModel: AppListViewModel by viewModels {
        InjectorUtils.provideAppListViewModel(requireContext())
    }

    override fun subscribeList(binding: FragmentRecyclerviewBinding) {
        mViewModel.filteredAppModelList.observe(viewLifecycleOwner) { appModelList ->
            binding.epoxyRecyclerView.withModels {
                appModelList.forEach {
                    simple {
                        id(it.packageName)
                        primaryText(it.appName)
                        icon(it.icon)

                        onClick { _ ->
                            selectModel(it)
                        }
                    }
                }
            }
        }
    }

    override fun onSearchQuery(query: String?) {
        mViewModel.searchQuery.value = query
    }
}