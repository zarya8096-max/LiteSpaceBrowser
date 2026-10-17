package org.litespace.browser.ui

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

object DeviceAuth {
    fun verify(activity: FragmentActivity, title: String, failure: () -> Unit = {}, success: () -> Unit) {
        val allowed = (if(Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK) or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if(BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(activity, "Configure a device PIN or biometric first", Toast.LENGTH_LONG).show(); failure(); return
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if(activity.isFinishing || activity.isDestroyed) { failure(); return }
                // Legacy credentials can return before the owner has restarted.
                // Keep the result until resume instead of dropping it or showing
                // protected content while the Activity is in the background.
                activity.lifecycleScope.launch {
                    try {
                        withTimeout(30_000) { activity.lifecycle.withResumed { if(!activity.isFinishing) success() else failure() } }
                    } catch(_: CancellationException) { failure() }
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { failure() }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(allowed).build())
    }
}
