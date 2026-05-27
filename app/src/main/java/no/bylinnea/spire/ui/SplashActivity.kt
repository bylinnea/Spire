package no.bylinnea.spire.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.R

/**
 * Launch screen. Shows a brief animation then routes to onboarding or the main app.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon     = findViewById<TextView>(R.id.splashIcon)
        val title    = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        icon.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_fade_slide_in))
        title.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_fade_slide_in))
        subtitle.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_fade_in_delayed))

        icon.postDelayed({
            val destination = if (ApiKeyManager.isOnboardingComplete(this)) {
                // Returning user - go straight to the app
                Intent(this, MainActivity::class.java)
            } else {
                // First launch - show onboarding
                Intent(this, OnboardingActivity::class.java)
            }
            startActivity(destination)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2200)
    }
}