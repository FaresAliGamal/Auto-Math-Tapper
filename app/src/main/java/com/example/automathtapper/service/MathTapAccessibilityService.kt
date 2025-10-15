package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MathTapAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MathTapper", "Service connected.")
        startSolvingLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun startSolvingLoop() {
        val handler = Handler(Looper.getMainLooper())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        handler.post(object : Runnable {
            override fun run() {
                takeScreenshot(DISPLAY_ID_MAIN, mainExecutor) { result ->
                    val image = result?.hardwareBuffer?.let {
                        InputImage.fromMediaImage(result.image, 0)
                    } ?: return@takeScreenshot

                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val allText = visionText.text
                            Log.d("MathTapper", "Detected: $allText")

                            val match = Regex("(\\d+)\\s*([+\\-x*/])\\s*(\\d+)").find(allText)
                            if (match != null) {
                                val (a, op, b) = match.destructured
                                val result = when (op) {
                                    "+" -> a.toInt() + b.toInt()
                                    "-" -> a.toInt() - b.toInt()
                                    "x", "*" -> a.toInt() * b.toInt()
                                    "/" -> if (b.toInt() != 0) a.toInt() / b.toInt() else 0
                                    else -> 0
                                }

                                val answer = result.toString()
                                Log.d("MathTapper", "Answer = $answer")

                                visionText.textBlocks.forEach { block ->
                                    if (block.text.trim() == answer) {
                                        val box = block.boundingBox ?: return@forEach
                                        tapAt(
                                            box.centerX().toFloat(),
                                            box.centerY().toFloat()
                                        )
                                        Log.d("MathTapper", "Tapped on $answer ✅")
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("MathTapper", "OCR failed: ${it.message}")
                        }

                    result?.close()
                }

                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun tapAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
