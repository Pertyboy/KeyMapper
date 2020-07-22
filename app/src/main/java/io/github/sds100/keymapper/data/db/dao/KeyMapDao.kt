package io.github.sds100.keymapper.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.github.sds100.keymapper.data.model.KeyMap

/**
 * Created by sds100 on 18/02/20.
 */

@Dao
interface KeyMapDao {
    companion object {
        const val TABLE_NAME = "keymaps"
        const val KEY_ID = "id"
        const val KEY_FLAGS = "flags"
        const val KEY_ENABLED = "is_enabled"
        const val KEY_TRIGGER = "trigger"
        const val KEY_ACTION_LIST = "action_list"
        const val KEY_CONSTRAINT_LIST = "constraint_list"
        const val KEY_CONSTRAINT_MODE = "constraint_mode"
        const val KEY_FOLDER_NAME = "folder_name"
    }

    @Query("SELECT * FROM $TABLE_NAME WHERE $KEY_ID = (:id)")
    suspend fun getById(id: Long): KeyMap

    @Query("SELECT * FROM $TABLE_NAME")
    fun observeAll(): LiveData<List<KeyMap>>

    @Query("SELECT * FROM $TABLE_NAME")
    fun getAll(): List<KeyMap>

    @Query("UPDATE $TABLE_NAME SET $KEY_ENABLED=0")
    suspend fun disableAll()

    @Query("UPDATE $TABLE_NAME SET $KEY_ENABLED=1")
    suspend fun enableAll()

    @Query("UPDATE $TABLE_NAME SET $KEY_ENABLED=1 WHERE $KEY_ID in (:id)")
    suspend fun enableKeymapById(vararg id: Long)

    @Query("UPDATE $TABLE_NAME SET $KEY_ENABLED=0 WHERE $KEY_ID in (:id)")
    suspend fun disableKeymapById(vararg id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg keyMap: KeyMap)

    @Delete
    suspend fun delete(vararg keyMap: KeyMap)

    @Query("DELETE FROM $TABLE_NAME")
    suspend fun deleteAll()

    @Query("DELETE FROM $TABLE_NAME WHERE $KEY_ID in (:id)")
    suspend fun deleteById(vararg id: Long)

    @Update
    suspend fun update(vararg keyMap: KeyMap)
}