package io.github.sds100.keymapper.ui.fragment

import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.addRepeatingJob
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_CENTER
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_END
import com.google.android.material.tabs.TabLayoutMediator
import io.github.sds100.keymapper.*
import io.github.sds100.keymapper.data.model.ChooseAppStoreModel
import io.github.sds100.keymapper.data.viewmodel.*
import io.github.sds100.keymapper.databinding.DialogChooseAppStoreBinding
import io.github.sds100.keymapper.databinding.FragmentHomeBinding
import io.github.sds100.keymapper.domain.preferences.Keys
import io.github.sds100.keymapper.ui.TextListItem
import io.github.sds100.keymapper.ui.adapter.HomePagerAdapter
import io.github.sds100.keymapper.util.*
import io.github.sds100.keymapper.util.delegate.FixErrorDelegate
import io.github.sds100.keymapper.util.result.getFullMessage
import io.github.sds100.keymapper.worker.SeedDatabaseWorker
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.cancelButton
import splitties.alertdialog.appcompat.messageResource
import splitties.toast.toast
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt
import java.util.*

class HomeFragment : Fragment() {

    private val homeViewModel: HomeViewModel by activityViewModels {
        InjectorUtils.provideHomeViewModel(requireContext())
    }

    private val fingerprintMapListViewModel: FingerprintMapListViewModel by activityViewModels {
        InjectorUtils.provideFingerprintMapListViewModel(requireContext())
    }

    /**
     * Scoped to the lifecycle of the fragment's view (between onCreateView and onDestroyView)
     */
    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = _binding!!

    private val backupAllKeymapsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) {
            it ?: return@registerForActivityResult

            backupRestoreViewModel
                .backupAll(requireContext().contentResolver.openOutputStream(it))
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it ?: return@registerForActivityResult

            backupRestoreViewModel.restore(requireContext().contentResolver.openInputStream(it))
        }

    private val backupRestoreViewModel: BackupRestoreViewModel by activityViewModels {
        InjectorUtils.provideBackupRestoreViewModel(requireContext())
    }

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position == 0) {
                fab.show()
            } else {
                fab.hide()
            }
        }
    }

    private var quickStartGuideTapTarget: MaterialTapTargetPrompt? = null
    private lateinit var fixErrorDelegate: FixErrorDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        fixErrorDelegate = FixErrorDelegate(
            "HomeFragment",
            requireActivity().activityResultRegistry,
            viewLifecycleOwner
        ) {
            homeViewModel.rebuildUiState()
        }

        FragmentHomeBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            _binding = this
            return this.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            viewModel = this@HomeFragment.homeViewModel

            val pagerAdapter = HomePagerAdapter(
                this@HomeFragment,
                fingerprintMapListViewModel.fingerprintGesturesAvailable.value ?: false
            )

            viewPager.adapter = pagerAdapter

            fingerprintMapListViewModel.fingerprintGesturesAvailable.observe(viewLifecycleOwner, {
                pagerAdapter.invalidateFragments(it ?: false)
                isFingerprintGestureDetectionAvailable = it ?: false
            })

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = strArray(R.array.home_tab_titles)[position]
            }.apply {
                attach()
            }

            viewPager.registerOnPageChangeCallback(onPageChangeCallback)

            appBar.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_help -> {
                        UrlUtils.launchCustomTab(
                            requireContext(),
                            str(R.string.url_quick_start_guide)
                        )
                        true
                    }

                    R.id.action_seed_database -> {
                        val request = OneTimeWorkRequestBuilder<SeedDatabaseWorker>().build()
                        WorkManager.getInstance(requireContext()).enqueue(request)
                        true
                    }

                    R.id.action_select_all -> {
                        homeViewModel.onSelectAllClick()
                        true
                    }

                    R.id.action_enable -> {
                        homeViewModel.onEnableSelectedKeymapsClick()
                        true
                    }

                    R.id.action_disable -> {
                        homeViewModel.onDisableSelectedKeymapsClick()
                        true
                    }

                    R.id.action_duplicate_keymap -> {
                        homeViewModel.onDuplicateSelectedKeymapsClick()
                        true
                    }

                    R.id.action_backup -> {
                        homeViewModel.onBackupSelectedKeymapsClick()
                        true
                    }

                    else -> false
                }
            }

            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                homeViewModel.onBackPressed()
            }

            appBar.setNavigationOnClickListener {
                homeViewModel.onAppBarNavigationButtonClick()
            }

            backupRestoreViewModel.eventStream.observe(viewLifecycleOwner, {
                when (it) {
                    is MessageEvent -> toast(it.textRes)
                    is ShowErrorMessage -> toast(it.error.getFullMessage(requireContext()))
                    is RequestRestore -> restoreLauncher.launch(FileUtils.MIME_TYPE_ALL)
                    is RequestBackupAll ->
                        backupAllKeymapsLauncher.launch(BackupUtils.createFileName())
                }
            })

            setGetNewGuiKeyboard {
                requireContext().alertDialog {
                    messageResource = R.string.dialog_message_select_app_store_gui_keyboard

                    DialogChooseAppStoreBinding.inflate(layoutInflater).apply {
                        model = ChooseAppStoreModel(
                            playStoreLink = str(R.string.url_play_store_keymapper_gui_keyboard),
                            githubLink = str(R.string.url_github_keymapper_gui_keyboard),
                            fdroidLink = str(R.string.url_fdroid_keymapper_gui_keyboard)
                        )

                        setView(this.root)
                    }

                    cancelButton()

                    show()
                }
            }

            viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
                homeViewModel.fixError.collectLatest {
                    fixErrorDelegate.recover(requireContext(), it, findNavController())
                }
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.appBarState.collectLatest {
                if (it == HomeAppBarState.MULTI_SELECTING) {
                    binding.appBar.setFabAlignmentModeAndReplaceMenu(
                        FAB_ALIGNMENT_MODE_END,
                        R.menu.menu_multi_select
                    )
                } else {
                    binding.appBar.setFabAlignmentModeAndReplaceMenu(
                        FAB_ALIGNMENT_MODE_CENTER,
                        R.menu.menu_home
                    )
                }
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.onboardingState.collectLatest {
                if (it.showQuickStartGuideTapTarget) {
                    if (quickStartGuideTapTarget?.state != MaterialTapTargetPrompt.STATE_REVEALED) {
                        quickStartGuideTapTarget?.dismiss()

                        delay(500)

                        quickStartGuideTapTarget =
                            QuickStartGuideTapTarget().show(this@HomeFragment, R.id.action_help) {
                                homeViewModel.approvedQuickStartGuideTapTarget()
                            }
                    }
                }

                if (it.showWhatsNew) {
                    val direction = NavAppDirections.actionGlobalOnlineFileFragment(
                        R.string.whats_new,
                        R.string.url_changelog
                    )
                    findNavController().navigate(direction)

                    homeViewModel.approvedWhatsNew()
                }
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.tabsState.collectLatest {
                binding.viewPager.isUserInputEnabled = it.enableViewPagerSwiping
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.navigateToCreateKeymapScreen.collectLatest {
                val direction = HomeFragmentDirections.actionToConfigKeymap()
                findNavController().navigate(direction)
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.showMenu.collectLatest {
                findNavController().navigate(R.id.action_global_menuFragment)
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.closeKeyMapper.collectLatest {
                requireActivity().finish()
            }
        }

        viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
            homeViewModel.errorListState.collectLatest { state ->
                binding.recyclerViewError.isVisible = state.isVisible

                binding.recyclerViewError.withModels {
                    state.listItems.forEach { listItem ->
                        if (listItem is TextListItem.Error) {
                            fixError {
                                id(listItem.id)

                                model(listItem)

                                onFixClick { _ ->
                                    homeViewModel.onFixErrorListItemClick(listItem.id)
                                }
                            }
                        }

                        if (listItem is TextListItem.Success) {
                            success {
                                id(listItem.id)

                                model(listItem)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        homeViewModel.rebuildUiState()

        if (PackageUtils.isAppInstalled(
                requireContext(),
                KeyboardUtils.KEY_MAPPER_GUI_IME_PACKAGE
            )
            || Build.VERSION.SDK_INT < KeyboardUtils.KEY_MAPPER_GUI_IME_MIN_API
        ) {
            ServiceLocator.preferenceRepository(requireContext()).set(Keys.showGuiKeyboardAd, false)
        }

        ServiceLocator.notificationController(requireContext())
            .onEvent(DismissFingerprintFeatureNotification)
    }

    override fun onDestroyView() {
        binding.viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
        _binding = null
        super.onDestroyView()
    }
}