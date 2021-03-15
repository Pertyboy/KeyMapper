package io.github.sds100.keymapper.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.github.sds100.keymapper.ServiceLocator
import io.github.sds100.keymapper.domain.usecases.OnboardingUseCaseImpl

/**
 * Created by sds100 on 20/01/21.
 */
class SplashActivity : FragmentActivity() {

    private val useCase = OnboardingUseCaseImpl(ServiceLocator.preferenceRepository(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            !useCase.shownAppIntro ->
                startActivity(Intent(this, AppIntroActivity::class.java))

            !useCase.approvedFingerprintFeaturePrompt
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                startActivity(Intent(this, FingerprintGestureIntroActivity::class.java))

            else -> startActivity(Intent(this, MainActivity::class.java))
        }

        finish()
    }
}