package com.example.automathtapper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.example.automathtapper.databinding.ActivityMainBinding
import android.content.Intent

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btn_open_settings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }
}

override fun onResume() {
    super.onResume()
    binding.btn_select_regions.setOnClickListener {
        startActivity(Intent(this, SelectAreaActivity::class.java))
    }
}
