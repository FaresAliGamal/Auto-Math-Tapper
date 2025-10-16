package com.example.automathtapper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.automathtapper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "amt_prefs"
        const val KEY_INTERVAL_MS = "interval_ms"
        const val DEFAULT_INTERVAL = 700
        const val MIN_INTERVAL = 200
        const val MAX_INTERVAL = 2000
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(KEY_INTERVAL_MS, DEFAULT_INTERVAL)
        binding.seekSpeed.max = MAX_INTERVAL - MIN_INTERVAL
        binding.seekSpeed.progress = (current - MIN_INTERVAL).coerceIn(0, binding.seekSpeed.max)
        binding.tvSpeed.text = "Speed: ${current} ms"

        binding.seekSpeed.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val value = MIN_INTERVAL + progress
                binding.tvSpeed.text = "Speed: ${value} ms"
                prefs.edit().putInt(KEY_INTERVAL_MS, value).apply()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
