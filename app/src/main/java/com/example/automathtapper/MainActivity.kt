package com.example.automathtapper

import com.example.automathtapper.ErrorBus
import com.example.automathtapper.ErrorOverlay

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREFS = "prefs"
        const val KEY_INTERVAL_MS = "interval_ms"
        const val DEFAULT_INTERVAL = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ErrorOverlay.init(applicationContext)
        ErrorBus.post("Ready")

        val sb = SeekBar(this)
        sb.max = 4800
        sb.progress = (getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_INTERVAL_MS, DEFAULT_INTERVAL) - 200)
        sb.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                val ms = p1 + 200
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_INTERVAL_MS, ms).apply()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        setContentView(sb)
    }
}
