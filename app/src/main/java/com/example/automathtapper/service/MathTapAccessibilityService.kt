package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper

class MathTapAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MathTapper", "Service connected")
        startSolvingLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun startSolvingLoop() {
        val handler = Handler(Looper.getMainLooper())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        handler.post(object : Runnable {
            override fun run() {
                try {
                    val display = display ?: return
                    val width = display.width
                    val height = display.height

                    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                    display.requestCaptureSurface(reader.surface)

                    val image = reader.acquireLatestImage() ?: return
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val input = InputImage.fromBitmap(bitmap, 0)

                    recognizer.process(input)
                        .addOnSuccessListener { result ->
                            val text = result.text
                            Log.d("MathTapper", "Detected text: $text")
                            val match = Regex("(\\d+)\\s*([+\\-x*/])\\s*(\\d+)").find(text)
                            if (match != null) {
                                val (a, op, b) = match.destructured
                                val calc = when (op) {
                                    "+" -> a.toInt() + b.toInt()
                                    "-" -> a.toInt() - b.toInt()
                                    "x", "*" -> a.toInt() * b.toInt()
                                    "/" -> if (b.toInt() != 0) a.toInt() / b.toInt() else 0
                                    else -> 0
                                }
                                Log.d("MathTapper", "Answer: $calc")
                                result.textBlocks.forEach { block ->
                                    if (block.text.trim() == calc.toString()) {
                                        val rect = block.boundingBox
                                        if (rect != null) tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("MathTapper", "OCR failed: ${it.message}")
                        }

                    reader.close()
                } catch (e: Exception) {
                    Log.e("MathTapper", "Loop error: ${e.message}")
                }

                handler.postDelayed(this, 3000)
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
