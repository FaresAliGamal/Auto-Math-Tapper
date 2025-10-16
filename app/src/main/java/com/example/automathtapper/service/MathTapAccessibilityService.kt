package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.automathtapper.MainActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

class MathTapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var isRunning = false
    private var overlayBtn: TextView? = null
    private var overlayStatus: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        addFloatingUI()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun getIntervalMs(): Long {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        return prefs.getInt(MainActivity.KEY_INTERVAL_MS, MainActivity.DEFAULT_INTERVAL)
            .toLong()
            .coerceIn(MainActivity.MIN_INTERVAL.toLong(), MainActivity.MAX_INTERVAL.toLong())
    }

    private fun startLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    captureAndSolveApi33()
                }
                handler.postDelayed(this, getIntervalMs())
            }
        })
    }

    private fun addFloatingUI() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val btn = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setPadding(28, 20, 28, 20)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7F6200EE"))
            setOnClickListener {
                isRunning = !isRunning
                text = if (isRunning) "⏸" else "▶"
                showStatus(if (isRunning) "Started" else "Paused")
            }
        }
        val lpBtn = WindowManager.LayoutParams(
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
        wm.addView(btn, lpBtn)
        overlayBtn = btn

        val status = TextView(this).apply {
            text = "Ready"
            textSize = 14f
            setPadding(16, 12, 16, 12)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#AA000000"))
        }
        val lpStatus = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
        wm.addView(status, lpStatus)
        overlayStatus = status
    }

    private fun showStatus(msg: String) {
        overlayStatus?.post { overlayStatus?.text = msg }
        Log.d("MathTapper", msg)
    }

    private fun toAsciiDigits(s: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val eastern = "۰۱۲۳۴۵۶۷۸۹"
        val sb = StringBuilder(s.length)
        s.forEach { ch ->
            val idxA = arabic.indexOf(ch)
            val idxE = eastern.indexOf(ch)
            when {
                idxA >= 0 -> sb.append('0' + idxA)
                idxE >= 0 -> sb.append('0' + idxE)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun toArabicIndicDigits(s: String): String {
        val map = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
        val sb = StringBuilder(s.length)
        s.forEach { ch -> if (ch in '0'..'9') sb.append(map[ch - '0']) else sb.append(ch) }
        return sb.toString()
    }

    private fun toEasternDigits(s: String): String {
        val map = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        val sb = StringBuilder(s.length)
        s.forEach { ch -> if (ch in '0'..'9') sb.append(map[ch - '0']) else sb.append(ch) }
        return sb.toString()
    }

    private fun normalizeEquation(raw: String): String {
        val s1 = toAsciiDigits(raw)
            .replace('×', '*')
            .replace('x', '*')
            .replace('X', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace('—', '-')
        return s1.replace("[=?:]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun parseAndSolve(text: String): Pair<String, String>? {
        val n = normalizeEquation(text)
        val m = Regex("(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)").find(n) ?: return null
        val (a, op, b) = m.destructured
        val ans = when (op) {
            "+" -> a.toLong() + b.toLong()
            "-" -> a.toLong() - b.toLong()
            "*" -> a.toLong() * b.toLong()
            "/" -> if (b.toLong() != 0L) a.toLong() / b.toLong() else 0L
            else -> Long.MIN_VALUE
        }.toString()
        return n to ans
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun captureAndSolveApi33() {
        try {
            val displayId = Display.DEFAULT_DISPLAY
            val exec: Executor = mainExecutor

            takeScreenshot(displayId, exec, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    try {
                        if (bmp != null) {
                            val image = InputImage.fromBitmap(bmp, 0)
                            recognizer.process(image)
                                .addOnSuccessListener { txt ->
                                    val parsed = parseAndSolve(txt.text)
                                    if (parsed == null) {
                                        showStatus("No equation")
                                        return@addOnSuccessListener
                                    }
                                    val (equation, answerAscii) = parsed
                                    val candidates = setOf(
                                        answerAscii,
                                        toArabicIndicDigits(answerAscii),
                                        toEasternDigits(answerAscii)
                                    )
                                    showStatus("$equation = $answerAscii")

                                    loop@ for (block in txt.textBlocks) {
                                        for (line in block.lines) {
                                            for (el in line.elements) {
                                                val elNorm = normalizeEquation(el.text)
                                                if (elNorm in candidates) {
                                                    val r = el.boundingBox ?: continue
                                                    tapAt(r.centerX().toFloat(), r.centerY().toFloat())
                                                    showStatus("Tapped $elNorm")
                                                    break@loop
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    showStatus("OCR error")
                                    Log.e("MathTapper", "OCR error: ${e.message}")
                                }
                        } else {
                            showStatus("Bitmap null")
                        }
                    } finally {
                        result.hardwareBuffer.close()
                        bmp?.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    showStatus("Screenshot failed: $errorCode")
                    Log.e("MathTapper", "Screenshot failed code=$errorCode")
                }
            })
        } catch (e: Throwable) {
            showStatus("Exception: ${e.message}")
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
        overlayBtn?.let { runCatching { wm.removeView(it) } }
        overlayStatus?.let { runCatching { wm.removeView(it) } }
        overlayBtn = null
        overlayStatus = null
    }

    private fun showStatusReady() = showStatus("Ready")
}
