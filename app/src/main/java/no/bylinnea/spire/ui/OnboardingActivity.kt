package no.bylinnea.spire.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import no.bylinnea.spire.util.ApiKeyManager
import no.bylinnea.spire.R

/**
 * First-launch screen for setting up AI features. Shown once until completed or skipped.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val anthropicInput  = findViewById<EditText>(R.id.inputAnthropicKey)
        val plantnetInput   = findViewById<EditText>(R.id.inputPlantNetKey)
        val btnEnable       = findViewById<TextView>(R.id.btnEnableAi)
        val btnSkip         = findViewById<TextView>(R.id.btnSkip)
        val keysSection     = findViewById<View>(R.id.keysSection)
        val btnShowKeys     = findViewById<TextView>(R.id.btnShowKeys)

        btnShowKeys.setOnClickListener {
            keysSection.visibility = View.VISIBLE
            btnShowKeys.visibility = View.GONE
        }

        btnEnable.setOnClickListener {
            val anthropicKey = anthropicInput.text.toString().trim()
            val plantnetKey  = plantnetInput.text.toString().trim()

            // At least one key is required
            if (anthropicKey.isEmpty() && plantnetKey.isEmpty()) {
                anthropicInput.error = "Enter at least one API key"
                return@setOnClickListener
            }

            if (anthropicKey.isNotEmpty()) {
                if (!anthropicKey.startsWith("sk-ant-")) {
                    anthropicInput.error = "Anthropic keys start with sk-ant-"
                    return@setOnClickListener
                }
                ApiKeyManager.saveAnthropicKey(this, anthropicKey)
            }

            if (plantnetKey.isNotEmpty()) {
                ApiKeyManager.savePlantNetKey(this, plantnetKey)
            }

            ApiKeyManager.setAiEnabled(this, true)
            ApiKeyManager.setOnboardingComplete(this, true)

            goToMain()
        }

        btnSkip.setOnClickListener {
            // AI stays off but onboarding is not marked complete,
            // so the user can re-run it from Settings to add keys later
            ApiKeyManager.setAiEnabled(this, false)
            ApiKeyManager.setOnboardingComplete(this, true)
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}