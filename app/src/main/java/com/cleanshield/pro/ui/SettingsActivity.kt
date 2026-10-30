package com.cleanshield.pro.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cleanshield.pro.CleanShieldApp
import com.cleanshield.pro.R
import com.cleanshield.pro.data.SettingsRepository

class SettingsActivity : AppCompatActivity() {
    private val settings by lazy { CleanShieldApp.instance.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Privacy & DNS settings"

        val provider = findViewById<Spinner>(R.id.dohProviderSpinner)
        val customEndpoint = findViewById<EditText>(R.id.customDohEndpoint)
        val customLabel = findViewById<TextView>(R.id.customEndpointLabel)
        val save = findViewById<Button>(R.id.saveSettingsButton)
        val boot = findViewById<android.widget.Switch>(R.id.switchStartOnBoot)

        val options = listOf(
            SettingsRepository.PROVIDER_CLOUDFLARE,
            SettingsRepository.PROVIDER_QUAD9,
            SettingsRepository.PROVIDER_CUSTOM
        )
        provider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        provider.setSelection(options.indexOf(settings.dohProvider).coerceAtLeast(0))
        customEndpoint.setText(settings.customDohEndpoint)
        boot.isChecked = settings.startOnBoot

        fun updateCustomVisibility() {
            val visible = provider.selectedItem == SettingsRepository.PROVIDER_CUSTOM
            customLabel.visibility = if (visible) View.VISIBLE else View.GONE
            customEndpoint.visibility = if (visible) View.VISIBLE else View.GONE
        }
        provider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCustomVisibility()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        updateCustomVisibility()

        boot.setOnCheckedChangeListener { _, checked -> settings.startOnBoot = checked }

        save.setOnClickListener {
            val selected = provider.selectedItem?.toString() ?: SettingsRepository.PROVIDER_CLOUDFLARE
            if (selected == SettingsRepository.PROVIDER_CUSTOM) {
                val endpoint = customEndpoint.text.toString().trim()
                val uri = Uri.parse(endpoint)
                if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                    customEndpoint.error = "Enter a valid HTTPS DNS-over-HTTPS URL"
                    return@setOnClickListener
                }
                settings.customDohEndpoint = endpoint
            }
            settings.dohProvider = selected
            Toast.makeText(this, "DNS settings saved. Restart protection to apply.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
