package io.github.sds100.keymapper.data.repository

import io.github.sds100.keymapper.data.model.SystemActionDef
import io.github.sds100.keymapper.util.result.Error

/**
 * Created by sds100 on 17/05/2020.
 */
interface SystemActionRepository {
    val supportedSystemActions: List<SystemActionDef>
    val unsupportedSystemActions: Map<SystemActionDef, Error>
}