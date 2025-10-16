package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.example.automathtapper.ProjectionStore
import com.example.automathtapper.MainActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer

class MathTapAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var running = false
    private var btn: TextView? = null
    private var status: TextView? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        addFloating()
        loop()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    private fun addFloating() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        status = TextView(this).apply {
            text = "Ready"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(16,12,16,12)
        }
        wm.addView(status, WindowLayout.params(24,120))
        btn = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7F6200EE"))
            setPadding(28,20,28,20)
            setOnClickListener {
                running = !running
                text = if (running) "⏸" else "▶"
                if (running) {
                    if (!ProjectionStore.hasProjection()) {
                        val i = Intent(this@MathTapAccessibilityService, com.example.automathtapper.RequestProjectionActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                        status?.text = "Grant capture"
                        return@setOnClickListener
                    }
                    ProjectionFgService.ensureRunning(this@MathTapAccessibilityService)
                    status?.text = "Started"
                } else {
                    status?.text = "Paused"
                }
            }
        }
        wm.addView(btn, WindowLayout.params(24,200))
    }
    private fun statusText(s: String) { status?.post { status?.text = s }; Log.d("MathTap", s) }
    private fun interval(): Long {
        val p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        return p.getInt(MainActivity.KEY_INTERVAL_MS, MainActivity.DEFAULT_INTERVAL).toLong()
    }
    private fun loop() {
        handler.post(object : Runnable {
            override fun run() {
                if (running) captureSolveTap()
                handler.postDelayed(this, interval())
            }
        })
    }
    private fun normalize(t: String): String {
        val ar = "٠١٢٣٤٥٦٧٨٩"; val fa = "۰۱۲۳۴۵۶۷۸۹"
        val out = StringBuilder()
        for (c in t) {
            val ia = ar.indexOf(c); val ifa = fa.indexOf(c)
            when {
                ia >= 0 -> out.append('0' + ia)
                ifa >= 0 -> out.append('0' + ifa)
                else -> out.append(c)
            }
        }
        return out.toString()
            .replace('×','*').replace('x','*').replace('X','*')
            .replace('÷','/').replace('−','-').replace('—','-')
            .replace("[=?:]".toRegex()," ").replace("\\s+".toRegex()," ").trim()
    }
    private fun parseSolve(text: String): Pair<String,String>? {
        val n = normalize(text)
        val m = Regex("(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)").find(n) ?: return null
        val (a,op,b) = m.destructured
        val ans = when(op) {
            "+" -> a.toLong()+b.toLong()
            "-" -> a.toLong()-b.toLong()
            "*" -> a.toLong()*b.toLong()
            "/" -> if (b.toLong() != 0L) a.toLong()/b.toLong() else 0L
            else -> 0L
        }.toString()
        return n to ans
    }
    private fun captureSolveTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (!ProjectionStore.hasProjection()) {
            statusText("Need capture permission")
            return
        }
        val proj = ProjectionStore.getProjection(this) ?: run {
            statusText("Projection null")
            return
        }
        if (!ProjectionFgService.ready) {
            statusText("Waiting FG service")
            ProjectionFgService.ensureRunning(this)
            return
        }
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels; val density = dm.densityDpi
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val vd = proj.createVirtualDisplay("auto_math_cap", w, h, density, 0, reader.surface, null, handler)
        handler.postDelayed({
            var img: Image? = null
            try {
                img = reader.acquireLatestImage()
                if (img == null) { statusText("No frame"); return@postDelayed }
                val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
                val buf = img.planes[0].buffer
                bmp.copyPixelsFromBuffer(buf)
                val input = InputImage.fromBitmap(bmp, 0)
                recognizer.process(input)
                    .addOnSuccessListener { txt ->
                        val parsed = parseSolve(txt.text)
                        if (parsed == null) { statusText("No eq"); return@addOnSuccessListener }
                        val (eq, ansAscii) = parsed
                        statusText("$eq = $ansAscii")
                        loop@ for (b in txt.textBlocks) {
                            for (l in b.lines) for (e in l.elements) {
                                if (normalize(e.text) == ansAscii) {
                                    val r = e.boundingBox ?: continue
                                    val px = r.centerX().toFloat(); val py = r.centerY().toFloat()
                                    val path = android.graphics.Path().apply { moveTo(px, py) }
                                    val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
                                    dispatchGesture(g, null, null)
                                    statusText("Tap $ansAscii")
                                    break@loop
                                }
                            }
                        }
                    }
                    .addOnFailureListener { statusText("OCR err") }
                bmp.recycle()
            } finally {
                img?.close()
                vd.release()
                reader.close()
            }
        }, 120)
    }
    override fun onDestroy() {
        super.onDestroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        btn?.let { runCatching { wm.removeView(it) } }
        status?.let { runCatching { wm.removeView(it) } }
        btn = null; status = null
    }
    private object WindowLayout {
        fun params(x: Int, y: Int): WindowManager.LayoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
    }
}
