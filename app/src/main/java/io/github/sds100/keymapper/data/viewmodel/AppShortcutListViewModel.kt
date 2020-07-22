package io.github.sds100.keymapper.data.viewmodel

import androidx.lifecycle.*
import io.github.sds100.keymapper.data.SystemRepository
import io.github.sds100.keymapper.data.model.AppShortcutListItemModel
import io.github.sds100.keymapper.ui.callback.ProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Created by sds100 on 27/01/2020.
 */
class AppShortcutListViewModel internal constructor(
    private val repository: SystemRepository
) : ViewModel(), ProgressCallback {

    val searchQuery: MutableLiveData<String> = MutableLiveData("")

    private val mAppShortcutModelList = liveData {
        loadingContent.value = true

        val appShortcutList = withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
            repository.getAppShortcutList().map {
                //only include it if it has a configuration screen

                val name = repository.getIntentLabel(it) ?: ""
                val icon = repository.getIntentIcon(it)

                AppShortcutListItemModel(it.activityInfo, name, icon)
            }.sortedBy { it.label.toLowerCase(Locale.getDefault()) }
        }

        emit(appShortcutList)

        loadingContent.value = false
    }

    val filteredAppShortcutModelList = MediatorLiveData<List<AppShortcutListItemModel>>().apply {
        fun filter(query: String) {
            mAppShortcutModelList.value?.filter {
                it.label.toLowerCase(Locale.getDefault()).contains(query)
            } ?: listOf()
        }

        addSource(searchQuery)
        { query ->
            filter(query)
        }

        addSource(mAppShortcutModelList)
        {
            value = it

            searchQuery.value?.let { query ->
                filter(query)
            }
        }
    }

    override val loadingContent = MutableLiveData(false)

    class Factory(
        private val mRepository: SystemRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>) =
            AppShortcutListViewModel(mRepository) as T
    }
}
