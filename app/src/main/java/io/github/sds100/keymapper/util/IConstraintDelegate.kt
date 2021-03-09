package io.github.sds100.keymapper.util

import io.github.sds100.keymapper.data.model.ConstraintEntity
import io.github.sds100.keymapper.data.model.ConstraintMode

/**
 * Created by sds100 on 13/12/20.
 */
interface IConstraintDelegate {
    fun Array<ConstraintEntity>.constraintsSatisfied(@ConstraintMode mode: Int): Boolean
}