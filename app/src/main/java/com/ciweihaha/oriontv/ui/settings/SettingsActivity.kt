package com.ciweihaha.oriontv.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.settings.AppSettings
import com.ciweihaha.oriontv.data.settings.normalizeM3uUrl
import com.ciweihaha.oriontv.databinding.ActivitySettingsBinding

/** Settings: M3U live source address. Saved raw, normalized on save. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { AppSettings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.m3uUrlInput.setText(settings.getM3uUrl().orEmpty())

        binding.saveButton.setOnClickListener {
            val normalized = normalizeM3uUrl(binding.m3uUrlInput.text.toString())
            if (normalized == null) {
                binding.statusText.text = getString(R.string.m3u_invalid)
                return@setOnClickListener
            }
            settings.setM3uUrl(normalized)
            Toast.makeText(this, R.string.m3u_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.clearButton.setOnClickListener {
            settings.setM3uUrl(null)
            binding.m3uUrlInput.setText("")
            binding.statusText.text = getString(R.string.m3u_cleared)
        }
    }
}
