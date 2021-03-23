package io.github.sds100.keymapper.ui.adapter

import android.os.Build
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.data.model.TabFragmentModel
import io.github.sds100.keymapper.ui.fragment.*
import io.github.sds100.keymapper.ui.keyevent.KeyCodeListFragment
import io.github.sds100.keymapper.ui.shortcuts.AppShortcutListFragment

/**
 * Created by sds100 on 26/01/2020.
 */

class ChooseActionPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    val tabFragmentCreators: List<TabFragmentModel> = mutableListOf(
        TabFragmentModel(R.string.action_type_title_application, AppListFragment.SEARCH_STATE_KEY) {
            AppListFragment().apply {
                isAppBarVisible = false
            }
        },
        TabFragmentModel(R.string.action_type_title_application_shortcut,
            AppShortcutListFragment.SEARCH_STATE_KEY) {

            AppShortcutListFragment().apply {
                isAppBarVisible = false
            }
        },
        TabFragmentModel(
            R.string.action_type_title_key_code,
            KeyCodeListFragment.SEARCH_STATE_KEY
        ) {

            KeyCodeListFragment().apply {
                isAppBarVisible = false
            }
        },

        TabFragmentModel(
            R.string.action_type_title_system_action,
            SystemActionListFragment.SEARCH_STATE_KEY
        ) {

            SystemActionListFragment().apply {
                isAppBarVisible = false
            }
        },

        TabFragmentModel(R.string.action_type_title_key, null) {
            KeyActionTypeFragment()
        },

        TabFragmentModel(R.string.action_type_title_keyevent, null) {
            ConfigKeyEventFragment()
        },

        TabFragmentModel(R.string.action_type_title_text_block, null) {
            TextBlockActionTypeFragment()
        },

        TabFragmentModel(R.string.action_type_url, null) {
            UrlActionTypeFragment()
        },

        TabFragmentModel(R.string.action_type_intent, null) {
            IntentActionTypeFragment()
        },

        TabFragmentModel(R.string.action_type_phone_call, null) {
            PhoneCallActionTypeFragment()
        },

        TabFragmentModel(R.string.tab_unsupported_actions, null) {

            UnsupportedActionListFragment().apply {
                isAppBarVisible = false
            }
        }
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val model = TabFragmentModel(R.string.action_type_tap_coordinate, null) {
                TapCoordinateActionTypeFragment()
            }

            add(5, model)
        }
    }

    override fun getItemCount() = tabFragmentCreators.size

    override fun createFragment(position: Int) =
        tabFragmentCreators[position].fragmentCreator.invoke()
}