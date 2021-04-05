package io.github.sds100.keymapper.mappings.fingerprintmaps

import io.github.sds100.keymapper.data.model.FingerprintMapEntity
import io.github.sds100.keymapper.domain.mappings.fingerprintmap.FingerprintMap
import io.github.sds100.keymapper.domain.mappings.fingerprintmap.FingerprintMapId

/**
 * Created by sds100 on 04/04/2021.
 */
data class FingerprintMapJsonGroup(val swipeDown: String,
                                   val swipeUp: String,
                                   val swipeLeft: String,
                                   val swipeRight: String)