
package com.example.automathtapper

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.example.automathtapper.ui.SelectView

class SelectAreaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_area)

        val selectView = findViewById<SelectView>(R.id.select_view)
        val btnQ = findViewById<Button>(R.id.btn_question)
        val btnA = findViewById<Button>(R.id.btn_answers)
        val btnR = findViewById<Button>(R.id.btn_reset)
        val btnS = findViewById<Button>(R.id.btn_save)

        btnQ.setOnClickListener { selectView.mode = SelectView.Mode.QUESTION }
        btnA.setOnClickListener { selectView.mode = SelectView.Mode.ANSWERS }
        btnR.setOnClickListener { selectView.reset() }
        btnS.setOnClickListener { 
            val p = getSharedPreferences("regions", MODE_PRIVATE).edit()
            selectView.questionRectNorm?.let { r -> 
                p.putFloat("qL", r.left); p.putFloat("qT", r.top); p.putFloat("qR", r.right); p.putFloat("qB", r.bottom)
            }
            selectView.answersRectNorm?.let { r -> 
                p.putFloat("aL", r.left); p.putFloat("aT", r.top); p.putFloat("aR", r.right); p.putFloat("aB", r.bottom)
            }
            p.apply()
            finish()
        }
    }
}
