package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.example.automathtapper.MainActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MathTapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var isRunning = false
    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        addFloatingButton()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun getIntervalMs(): Long {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        return prefs.getInt(MainActivity.KEY_INTERVAL_MS, MainActivity.DEFAULT_INTERVAL).toLong()
    }

    private fun startLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    captureAndSolveApi33()
                }
                handler.postDelayed(this, getIntervalMs().coerceIn(200, 2000))
            }
        })
    }

    private fun addFloatingButton() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val btn = android.widget.TextView(this).apply {
            text = "▶"
            textSize = 18f
            setPadding(28, 20, 28, 20)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7F6200EE"))
            setOnClickListener {
                isRunning = !isRunning
                text = if (isRunning) "⏸" else "▶"
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }
        wm.addView(btn, lp)
        overlayView = btn
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun captureAndSolveApi33() {
        try {
            takeScreenshot(DISPLAY_ID_MAIN, mainExecutor) { res ->
                if (res == null) return@takeScreenshot
                val bmp = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace)
                if (bmp != null) {
                    val image = InputImage.fromBitmap(bmp, 0)
                    recognizer.process(image)
                        .addOnSuccessListener { txt ->
                            val all = txt.text
                            val m = Regex("(\\d+)\\s*([+\\-x*/×÷])\\s*(\\d+)").find(all)
                            if (m != null) {
                                val (a, op, b) = m.destructured
                                val ans = when (op) {
                                    "+", "＋" -> a.toInt() + b.toInt()
                                    "-", "−" -> a.toInt() - b.toInt()
                                    "x", "×", "*" -> a.toInt() * b.toInt()
                                    "/", "÷" -> if (b.toInt() != 0) a.toInt() / b.toInt() else 0
                                    else -> Int.MIN_VALUE
                                }.toString()

                                txt.textBlocks.forEach { block ->
                                    if (block.text.trim() == ans) {
                                        block.boundingBox?.let { r ->
                                            tapAt(r.centerX().toFloat(), r.centerY().toFloat())
                                            Log.d("MathTapper", "Tap $ans at ${r.centerX()},${r.centerY()}")
                                            return@addOnSuccessListener
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e -> Log.e("MathTapper", "OCR error: ${e.message}") }
                }
                res.hardwareBuffer.close()
                bmp?.recycle()
            }
        } catch (e: Throwable) {
            Log.e("MathTapper", "screenshot failure: ${e.message}")
        }
    }

    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let { runCatching { wm.removeView(it) } }
        overlayView = null
    }
}
