package com.quizlock.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal

/**
 * Kids Lock: parent ka fingerprint. System ka fingerprint dialog sirf saamne
 * wali Activity se khulta hai, overlay se nahi, isliye ye chhoti si screen.
 * Phone ka PIN/pattern nahi maanga jaata, kyunki phone khula hai to bachcha
 * shayad wo jaanta hai.
 */
class BiometricActivity : Activity() {

    private val cancel = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.parseColor("#0F172A"))
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
        if (!available(this)) {
            finish()
            return
        }
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Parent permission")
            .setSubtitle("Unlock games for $minutes minutes")
            .setNegativeButton("Cancel", mainExecutor) { _, _ -> finish() }
            .apply { if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG) }
            .build()
        prompt.authenticate(cancel, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                Store(this@BiometricActivity).kidsFreeUntil = System.currentTimeMillis() + minutes * 60_000L
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                finish()
            }
        })
    }

    override fun onDestroy() {
        cancel.cancel()
        LockService.poke()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"

        /** Fingerprint laga hua hai? (Android 10+.) */
        fun available(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 29) return false
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            val result = if (Build.VERSION.SDK_INT >= 30) {
                bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            } else {
                bm.canAuthenticate()
            }
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }
    }
}
