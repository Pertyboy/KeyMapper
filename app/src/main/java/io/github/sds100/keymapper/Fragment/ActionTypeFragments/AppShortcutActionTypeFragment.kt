package io.github.sds100.keymapper.Fragment.ActionTypeFragments

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.sds100.keymapper.*
import io.github.sds100.keymapper.Action.Companion.EXTRA_PACKAGE_NAME
import io.github.sds100.keymapper.Action.Companion.EXTRA_SHORTCUT_TITLE
import io.github.sds100.keymapper.Adapters.AppShortcutAdapter
import io.github.sds100.keymapper.Interfaces.OnItemClickListener
import io.github.sds100.keymapper.Utils.AppShortcutUtils
import io.github.sds100.keymapper.Views.ShortcutTitleDialog
import kotlinx.android.synthetic.main.action_type_recyclerview.*

/**
 * Created by sds100 on 31/07/2018.
 */

/**
 * A Fragment which shows all the available shortcut widgets for each installed app which has them
 */
class AppShortcutActionTypeFragment : FilterableActionTypeFragment(), OnItemClickListener<ResolveInfo> {

    companion object {
        private const val REQUEST_CODE_SHORTCUT_CONFIGURATION = 837
    }

    private val mAppShortcutAdapter by lazy {
        AppShortcutAdapter(
                onItemClickListener = this,
                mAppShortcutList = AppShortcutUtils.getAppShortcuts(context!!.packageManager),
                mPackageManager = context!!.packageManager)
    }

    private var mTempShortcutPackageName: String? = null

    override val filterable
        get() = mAppShortcutAdapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.action_type_recyclerview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = mAppShortcutAdapter
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SHORTCUT_CONFIGURATION &&
                resultCode == Activity.RESULT_OK) {

            data ?: return

            val shortcutUri: String

            //the shortcut intents seem to be returned in 2 different formats.
            if (data.extras != null &&
                    data.extras!!.containsKey(Intent.EXTRA_SHORTCUT_INTENT)) {
                //get intent from selected shortcut
                val shortcutIntent = data.extras!!.get(Intent.EXTRA_SHORTCUT_INTENT) as Intent
                shortcutUri = shortcutIntent.toUri(0)

            } else {
                shortcutUri = data.toUri(0)
            }


            //show a dialog to prompt for a title.
            ShortcutTitleDialog.show(context!!) { title ->
                val extras = mutableListOf(
                        Extra(EXTRA_SHORTCUT_TITLE, title),
                        Extra(EXTRA_PACKAGE_NAME, mTempShortcutPackageName!!)
                )

                //save the shortcut intent as a URI
                val action = Action(ActionType.APP_SHORTCUT, shortcutUri, extras)
                chooseSelectedAction(action)

                mTempShortcutPackageName = null
            }

        }
    }

    override fun onItemClick(item: ResolveInfo) {
        val packageName = item.activityInfo.applicationInfo.packageName
        mTempShortcutPackageName = packageName

        //open the shortcut configuration screen when the user taps a shortcut
        val intent = Intent()
        intent.setClassName(packageName, item.activityInfo.name)
        startActivityForResult(intent, REQUEST_CODE_SHORTCUT_CONFIGURATION)
    }
}