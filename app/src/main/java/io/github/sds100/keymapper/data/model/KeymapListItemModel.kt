package io.github.sds100.keymapper.data.model

/**
 * Created by sds100 on 28/03/2020.
 */

//TODO a single list should be used for the chips
data class KeymapListItemModel(
    val id: Long,
    val actionList: List<ActionChipModel>,
    val triggerDescription: String,
    val constraintList: List<ConstraintModel>,
    val constraintMode: Int,
    val flagsDescription: String,
    val isEnabled: Boolean,
    val uid: String
) {
    val hasActions: Boolean
        get() = actionList.isNotEmpty()

    val hasTrigger: Boolean
        get() = triggerDescription.isNotBlank()

    val hasConstraints: Boolean
        get() = constraintList.isNotEmpty()

    val hasFlags: Boolean
        get() = flagsDescription.isNotBlank()

    val actionsHaveErrors: Boolean
        get() = actionList.any { it.hasError }
}