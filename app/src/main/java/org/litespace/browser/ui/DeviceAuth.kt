package org.litespace.browser.ui

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object DeviceAuth {
    fun verify(activity: FragmentActivity, title: String, failure: () -> Unit = {}, success: () -> Unit) {
        val allowed = (if(Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK) or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if(BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(activity, "Configure a device PIN or biometric first", Toast.LENGTH_LONG).show(); failure(); return
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if(activity.isFinishing || activity.isDestroyed || !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) failure()
                else success()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { failure() }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(allowed).build())
    }
}
