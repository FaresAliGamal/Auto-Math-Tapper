
package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.example.automathtapper.MathSolver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MathTapAccessibilityService : AccessibilityService() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastActionTs = 0L

    private fun withinRegions(rect: android.graphics.Rect, w: Int, h: Int, q: android.graphics.RectF?, a: android.graphics.RectF?, wantQuestion: Boolean): Boolean {
        val r = android.graphics.RectF(rect)
        val rn = android.graphics.RectF(r.left / w.toFloat(), r.top / h.toFloat(), r.right / w.toFloat(), r.bottom / h.toFloat())
        val target = if (wantQuestion) q else a
        return if (target == null) true else android.graphics.RectF.intersects(rn, target)
    }


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastActionTs < 250) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            takeAndProcessScreenshot()
        }
    }

    override fun onInterrupt() { }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun takeAndProcessScreenshot() {
        try {
            takeScreenshot(AccessibilityService.SCREENSHOT_CAPTURE_MODE_FULLSCREEN) { result ->
                val bmp = result?.hardwareBuffer?.let {
                    val hw = android.graphics.Bitmap.wrapHardwareBuffer(it, result.colorSpace)
                    hw?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)?.also { _ ->
                        hw.close()
                    }
                } ?: return@takeScreenshot

                val image = InputImage.fromBitmap(bmp, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        var question: Pair<String, Rect>? = null
                        val answers = mutableListOf<Pair<String, Rect>>()

                        val prefs = getSharedPreferences("regions", MODE_PRIVATE)
val q = if (prefs.contains("qL")) android.graphics.RectF(prefs.getFloat("qL",0f), prefs.getFloat("qT",0f), prefs.getFloat("qR",1f), prefs.getFloat("qB",1f)) else null
val a = if (prefs.contains("aL")) android.graphics.RectF(prefs.getFloat("aL",0f), prefs.getFloat("aT",0f), prefs.getFloat("aR",1f), prefs.getFloat("aB",1f)) else null
val screenW = bmp.width
val screenH = bmp.height
for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val txt = line.text.trim()
                                val rect = line.boundingBox ?: continue
                                if (Regex(".*[+\\-×xX÷/].*=.*").matches(txt) && withinRegions(rect, screenW, screenH, q, a, true)) {
                                    question = txt to rect
                                } else if (Regex("^\\d{1,3}$").matches(txt) && withinRegions(rect, screenW, screenH, q, a, false)) {
                                    answers.add(txt to rect)
                                }
                            }
                        }

                        val solution = question?.first?.let { MathSolver.solveSimple(it) }
                        if (solution != null) {
                            val candidate = answers.firstOrNull { it.first.toIntOrNull() == solution }
                            candidate?.let { (_, rect) -> tapCenter(rect) }
                            lastActionTs = System.currentTimeMillis()
                        }
                        bmp.recycle()
                    }
                    .addOnFailureListener { e -> Log.e(TAG, "OCR failed", e) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "screenshot error", t)
        }
    }

    private fun tapCenter(rect: Rect) {
        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder().apply { addStroke(stroke) }
        dispatchGesture(builder.build(), null, null)
    }

    companion object { private const val TAG = "MathTapService" }
}
