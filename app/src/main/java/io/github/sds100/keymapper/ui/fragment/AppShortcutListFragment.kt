package io.github.sds100.keymapper.ui.fragment

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.ServiceLocator
import io.github.sds100.keymapper.data.model.AppShortcutListItemModel
import io.github.sds100.keymapper.data.viewmodel.AppShortcutListViewModel
import io.github.sds100.keymapper.databinding.FragmentRecyclerviewBinding
import io.github.sds100.keymapper.simple
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.delegate.ModelState
import splitties.toast.toast

/**
 * Created by sds100 on 29/03/2020.
 */

class AppShortcutListFragment : DefaultRecyclerViewFragment<List<AppShortcutListItemModel>>() {

    companion object {
        const val REQUEST_KEY = "request_app_shortcut"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_URI = "extra_uri"
        const val SEARCH_STATE_KEY = "key_app_shortcut_search_state"
    }

    override var requestKey: String? = REQUEST_KEY
    override var searchStateKey: String? = SEARCH_STATE_KEY

    private val viewModel: AppShortcutListViewModel by activityViewModels {
        InjectorUtils.provideAppShortcutListViewModel(requireContext())
    }

    override val modelState: ModelState<List<AppShortcutListItemModel>>
        get() = viewModel

    private val appShortcutConfigLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it ?: return@registerForActivityResult

            it.apply {
                if (resultCode == Activity.RESULT_OK) {
                    data ?: return@apply

                    val uri: String

                    //the shortcut intents seem to be returned in 2 different formats.
                    @Suppress("DEPRECATION")
                    if (data?.extras != null &&
                        data?.extras!!.containsKey(Intent.EXTRA_SHORTCUT_INTENT)) {
                        //get intent from selected shortcut
                        val shortcutIntent = data?.extras!!.get(Intent.EXTRA_SHORTCUT_INTENT) as Intent
                        uri = shortcutIntent.toUri(0)

                    } else {
                        uri = data!!.toUri(0)
                    }

                    val packageName = Intent.parseUri(uri, 0).`package`
                        ?: data?.component?.packageName
                        ?: Intent.parseUri(uri, 0).component?.packageName

                    /*
                    must launch when resumed or started because setFragmentResult won't work otherwise!
                    don't launch when started because going up the nav tree will cause the parent fragment and this
                    fragment to overlap
                     */
                    lifecycleScope.launchWhenResumed {
                        val appName = if (packageName == null) {
                            null
                        } else {
                            ServiceLocator.packageRepository(requireContext()).getAppName(packageName)
                        }

                        val shortcutName = if (appName == null) {
                            getShortcutName(data!!)
                        } else {
                            "$appName: ${getShortcutName(data!!)}"
                        }

                        returnResult(
                            EXTRA_NAME to shortcutName,
                            EXTRA_PACKAGE_NAME to packageName,
                            EXTRA_URI to uri
                        )
                    }
                }
            }
        }

    override fun populateList(binding: FragmentRecyclerviewBinding,
                              modelList: List<AppShortcutListItemModel>?) {
        binding.epoxyRecyclerView.withModels {
            modelList?.forEach {
                simple {
                    id(it.activityInfo.name)
                    primaryText(it.label)
                    icon(it.icon)

                    onClick { _ ->
                        it.activityInfo.launchShortcutConfiguration()
                    }
                }
            }
        }
    }

    override fun onSearchQuery(query: String?) {
        viewModel.searchQuery.value = query
    }

    private fun ActivityInfo.launchShortcutConfiguration() {
        Intent().apply {
            action = Intent.ACTION_CREATE_SHORTCUT
            setClassName(packageName, name)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)

            try {
                appShortcutConfigLauncher.launch(this)
            } catch (e: SecurityException) {
                toast(R.string.error_keymapper_doesnt_have_permission_app_shortcut)
            }
        }
    }

    private suspend fun getShortcutName(data: Intent): String {
        @Suppress("DEPRECATION") var shortcutName: String? = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)

        if (shortcutName.isNullOrBlank()) {
            shortcutName =
                requireContext().editTextStringAlertDialog(
                    viewLifecycleOwner,
                    str(R.string.dialog_title_create_shortcut_title))
        }

        return shortcutName
    }
}