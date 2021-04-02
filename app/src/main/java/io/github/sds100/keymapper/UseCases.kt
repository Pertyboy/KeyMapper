package io.github.sds100.keymapper

import android.content.Context
import io.github.sds100.keymapper.domain.actions.GetActionErrorUseCaseImpl
import io.github.sds100.keymapper.domain.actions.IsSystemActionSupportedUseCaseImpl
import io.github.sds100.keymapper.domain.actions.TestActionUseCaseImpl
import io.github.sds100.keymapper.domain.constraints.GetConstraintErrorUseCaseImpl
import io.github.sds100.keymapper.domain.constraints.IsConstraintSupportedByDeviceUseCaseImpl
import io.github.sds100.keymapper.domain.devices.GetInputDevicesUseCaseImpl
import io.github.sds100.keymapper.domain.mappings.keymap.KeymapAction
import io.github.sds100.keymapper.domain.mappings.keymap.ListKeymapsUseCaseImpl
import io.github.sds100.keymapper.domain.permissions.IsAccessibilityServiceEnabledUseCase
import io.github.sds100.keymapper.domain.permissions.IsAccessibilityServiceEnabledUseCaseImpl
import io.github.sds100.keymapper.domain.permissions.IsBatteryOptimisedUseCaseImpl
import io.github.sds100.keymapper.domain.permissions.IsDoNotDisturbAccessGrantedImpl
import io.github.sds100.keymapper.domain.settings.GetSettingsUseCaseImpl
import io.github.sds100.keymapper.domain.usecases.OnboardingUseCaseImpl
import io.github.sds100.keymapper.ui.actions.ActionUiHelper
import io.github.sds100.keymapper.ui.mappings.keymap.KeymapActionUiHelper
import io.github.sds100.keymapper.ui.shortcuts.CreateKeymapShortcutUseCaseImpl
import io.github.sds100.keymapper.ui.shortcuts.IsRequestShortcutSupportedImpl

/**
 * Created by sds100 on 03/03/2021.
 */
object UseCases {

    fun keymapActionUiHelper(ctx: Context): ActionUiHelper<KeymapAction> {
        return KeymapActionUiHelper(
            ServiceLocator.appInfoAdapter(ctx),
            ServiceLocator.inputMethodAdapter(ctx),
            ServiceLocator.resourceProvider(ctx)
        )
    }

    fun getActionError(ctx: Context) = GetActionErrorUseCaseImpl(
        ServiceLocator.packageManagerAdapter(ctx),
        ServiceLocator.inputMethodAdapter(ctx),
        ServiceLocator.permissionAdapter(ctx),
        ServiceLocator.systemFeatureAdapter(ctx),
        ServiceLocator.cameraAdapter(ctx)
    )

    fun getConstraintError(ctx: Context) = GetConstraintErrorUseCaseImpl(
        ServiceLocator.packageManagerAdapter(ctx),
        ServiceLocator.permissionAdapter(ctx),
        ServiceLocator.systemFeatureAdapter(ctx),
    )

    fun testAction(ctx: Context) = TestActionUseCaseImpl()

    fun onboarding(ctx: Context) = OnboardingUseCaseImpl(ServiceLocator.preferenceRepository(ctx))

    fun getInputDevices(ctx: Context) = GetInputDevicesUseCaseImpl(
        ServiceLocator.deviceInfoRepository(ctx),
        ServiceLocator.preferenceRepository(ctx),
        ServiceLocator.externalDeviceAdapter(ctx)
    )

    fun recordTrigger(ctx: Context) =
        (ctx.applicationContext as KeyMapperApp).recordTriggerController

    fun listKeymaps(ctx: Context) = ListKeymapsUseCaseImpl(
        ServiceLocator.roomKeymapRepository(ctx)
    )

    fun isConstraintSupported(ctx: Context) =
        IsConstraintSupportedByDeviceUseCaseImpl(
            ServiceLocator.systemFeatureAdapter(ctx)
        )

    fun getSettings(ctx: Context) = GetSettingsUseCaseImpl(
        ServiceLocator.preferenceRepository(ctx)
    )

    fun isRequestShortcutSupported(ctx: Context) = IsRequestShortcutSupportedImpl(
        ServiceLocator.launcherShortcutAdapter(ctx)
    )

    fun createKeymapShortcut(ctx: Context) = CreateKeymapShortcutUseCaseImpl(
        ServiceLocator.launcherShortcutAdapter(ctx),
        ServiceLocator.resourceProvider(ctx),
        keymapActionUiHelper(ctx)
    )

    fun isSystemActionSupported(ctx: Context) =
        IsSystemActionSupportedUseCaseImpl(ServiceLocator.systemFeatureAdapter(ctx))

    fun isDndAccessGranted(ctx: Context) =
        IsDoNotDisturbAccessGrantedImpl(ServiceLocator.permissionAdapter(ctx))

    fun isBatteryOptimised(ctx: Context) =
        IsBatteryOptimisedUseCaseImpl(ServiceLocator.powerManagementAdapter(ctx))

    fun isAccessibilityServiceEnabled(ctx: Context) =
        IsAccessibilityServiceEnabledUseCaseImpl(ServiceLocator.serviceAdapter(ctx))
}