package io.github.sds100.keymapper.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.createDataStore
import io.github.sds100.keymapper.util.defaultSharedPreferences
import io.github.sds100.keymapper.util.str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Created by sds100 on 20/02/2020.
 */

class DefaultPreferenceDataStore(ctx: Context) : IPreferenceDataStore {

    private val mCtx = ctx.applicationContext

    private val mPrefs: SharedPreferences
        get() = mCtx.defaultSharedPreferences

    override val fingerprintGestureDataStore = ctx.createDataStore("fingerprint_gestures")
    private val preferenceDataStore = ctx.createDataStore("preferences")

    override fun getBoolPref(key: Int): Boolean {
        return mPrefs.getBoolean(mCtx.str(key), false)
    }

    override fun setBoolPref(key: Int, value: Boolean) {
        mPrefs.edit {
            putBoolean(mCtx.str(key), value)
        }
    }

    override fun <T> get(key: Preferences.Key<T>): Flow<T?> {
        return preferenceDataStore.data.map { it[key] }
    }

    override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        preferenceDataStore.edit {
            it[key] = value
        }
    }

    override fun getStringPref(key: Int): String? {
        return mPrefs.getString(mCtx.str(key), null)
    }

    override fun setStringPref(key: Int, value: String) {
        mPrefs.edit {
            putString(mCtx.str(key), value)
        }
    }
}