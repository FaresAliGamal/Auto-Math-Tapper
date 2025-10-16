#!/usr/bin/env bash
set -euo pipefail

branch="fix/ocr-mlkit"
git checkout -B "$branch"

mkdir -p app/src/main/java/com/example/automathtapper
mkdir -p app/src/main/java/com/example/automathtapper/service
mkdir -p app/src/main/res/xml
mkdir -p .github/workflows

cat > app/src/main/java/com/example/automathtapper/MainActivity.kt <<'KOT'
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
KOT

cat > app/src/main/java/com/example/automathtapper/service/MathTapAccessibilityService.kt <<'KOT'
package com.example.automathtapper.service

import com.example.automathtapper.ErrorBus
import com.example.automathtapper.ErrorOverlay

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
import com.example.automathtapper.MainActivity
import com.example.automathtapper.ProjectionStore
import com.example.automathtapper.RequestProjectionActivity
import com.example.automathtapper.service.ProjectionFgService
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
            setPadding(16, 12, 16, 12)
        }
        wm.addView(status, WindowLayout.params(24, 120))

        btn = TextView(this).apply {
            text = "\u25b6"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7F6200EE"))
            setPadding(28, 20, 28, 20)
            setOnClickListener {
                running = !running
                text = if (running) "\u23f8" else "\u25b6"
                if (running) {
                    ProjectionFgService.ensureRunning(this@MathTapAccessibilityService)
                    setStatus("Starting…")
                } else {
                    setStatus("Paused")
                }
            }
        }
        wm.addView(btn, WindowLayout.params(24, 200))
    }

    private fun setStatus(s: String) { status?.post { status?.text = s }; Log.d("MathTapper", s) }

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

    private fun toAsciiDigits(s: String): String {
        val ar = "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"; val fa = "\u06f0\u06f1\u06f2\u06f3\u06f4\u06f5\u06f6\u06f7\u06f8\u06f9"
        val out = StringBuilder(s.length)
        for (c in s) {
            val ia = ar.indexOf(c); val ifa = fa.indexOf(c)
            when {
                ia >= 0 -> out.append('0' + ia)
                ifa >= 0 -> out.append('0' + ifa)
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun normalize(t: String): String =
        toAsciiDigits(t)
            .replace('\u00d7','*').replace('x','*').replace('X','*')
            .replace('\u00f7','/').replace('\u2212','-').replace('\u2014','-')
            .replace("[=?:]".toRegex()," ")
            .replace("\\s+".toRegex()," ").trim()

    private fun parseSolve(text: String): Pair<String,String>? {
        val n = normalize(text)
        val m = Regex("(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)").find(n) ?: return null
        val (a,op,b) = m.destructured
        val ans = when(op){
            "+" -> a.toLong()+b.toLong()
            "-" -> a.toLong()-b.toLong()
            "*" -> a.toLong()*b.toLong()
            "/" -> if (b.toLong()!=0L) a.toLong()/b.toLong() else 0L
            else -> 0L
        }.toString()
        return n to ans
    }

    private fun captureSolveTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (!ProjectionFgService.ready) {
            setStatus("Waiting for foreground service…")
            ProjectionFgService.ensureRunning(this)
            return
        }
        if (!ProjectionStore.hasProjection()) {
            val i = Intent(this, RequestProjectionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            setStatus("Grant screen capture")
            return
        }

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val density = dm.densityDpi

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val proj = ProjectionStore.getProjection(this) ?: run { setStatus("Projection null"); return }

        val vd = proj.createVirtualDisplay("auto_math_cap", w, h, density, 0, reader.surface, null, handler)

        handler.postDelayed({
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image == null) { setStatus("No frame"); return@postDelayed }
                val bmp = imageToBitmap(image)
                val img = InputImage.fromBitmap(bmp, 0)
                recognizer.process(img)
                    .addOnSuccessListener { txt ->
                        val parsed = parseSolve(txt.text)
                        if (parsed == null) { setStatus("No equation"); return@addOnSuccessListener }
                        val (eq, ansAscii) = parsed
                        setStatus("$eq = $ansAscii")
                        loop@ for (b in txt.textBlocks) {
                            for (l in b.lines) for (e in l.elements) {
                                if (normalize(e.text) == ansAscii) {
                                    val r = e.boundingBox ?: continue
                                    tap(r.centerX().toFloat(), r.centerY().toFloat())
                                    setStatus("Tap $ansAscii")
                                    break@loop
                                }
                            }
                        }
                    }
                    .addOnFailureListener { setStatus("OCR error") }
                bmp.recycle()
            } finally {
                image?.close()
                vd.release()
                reader.close()
            }
        }, 120)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buf: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buf)
        return Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(g, null, null)
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
KOT

cat > app/src/main/java/com/example/automathtapper/service/ProjectionFgService.kt <<'KOT'
package com.example.automathtapper.service

import com.example.automathtapper.ErrorBus
import com.example.automathtapper.ErrorOverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProjectionFgService : Service() {
    companion object {
        @Volatile var ready: Boolean = false
        fun ensureRunning(ctx: android.content.Context) {
            try {
                val i = Intent(ctx, ProjectionFgService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(i)
                else
                    ctx.startService(i)
            } catch (e: Throwable) {
                ErrorBus.post("FGS: " + (e.message ?: e.toString()))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            ErrorOverlay.init(applicationContext)
            val cid = "proj_fg"
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(cid) == null)
                    nm.createNotificationChannel(NotificationChannel(cid, "Projection FG", NotificationManager.IMPORTANCE_LOW))
            }

            val notif: Notification = NotificationCompat.Builder(this, cid)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Projection FG")
                .setContentText("Ready")
                .setOngoing(true)
                .build()
            ErrorBus.post("Starting FGS")
            startForeground(1001, notif)
            ready = true
        } catch (e: Throwable) {
            ErrorBus.post("FGS: " + (e.message ?: e.toString()))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            ready = false
            super.onDestroy()
        } catch (e: Throwable) {
            ErrorBus.post("FGS: " + (e.message ?: e.toString()))
        }
    }
}
KOT

cat > app/src/main/res/xml/accessibility_service_config.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canPerformGestures="true"
    android:settingsActivity="" />
XML

manifest="app/src/main/AndroidManifest.xml"
if ! grep -q 'android.permission.FOREGROUND_SERVICE"' "$manifest" 2>/dev/null; then
  sed -i '1a <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>' "$manifest"
fi
sed -i 's#android:resource="@xml/accessibilityservice"#android:resource="@xml/accessibility_service_config"#g' "$manifest" || true
if ! grep -q 'service.MathTapAccessibilityService' "$manifest"; then
  sed -i '/<application[^>]*>/a \
    <service android:name=".service.MathTapAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="false">\
      <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>\
      <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config" />\
    </service>' "$manifest"
fi
if ! grep -q 'service.ProjectionFgService' "$manifest"; then
  sed -i '/<application[^>]*>/a \
    <service android:name=".service.ProjectionFgService" android:exported="false" android:foregroundServiceType="mediaProjection" />' "$manifest"
fi

appkts="app/build.gradle.kts"
sed -i 's/\bcompileSdk\s*=\s*3[0-3]\b/compileSdk = 34/g; s/\btargetSdk\s*=\s*3[0-3]\b/targetSdk = 34/g' "$appkts" || true
if ! grep -q 'text-recognition' "$appkts"; then
  awk '
    BEGIN{ins=0}
    /dependencies\s*\{/{
      print; print "    implementation(\"com.google.mlkit:text-recognition:16.1.0\")"; ins=1; next
    }
    {print}
    END{
      if(ins==0){print "dependencies {\n    implementation(\"com.google.mlkit:text-recognition:16.1.0\")\n}"}}
  ' "$appkts" > "$appkts.tmp" && mv "$appkts.tmp" "$appkts"
fi
if ! grep -q 'kotlinOptions' "$appkts"; then
  sed -i '/android\s*{/a \    kotlinOptions {\n        jvmTarget = "17"\n    }' "$appkts"
fi
if ! grep -q 'kotlin\s*{[^}]*jvmToolchain(17)' "$appkts"; then
  printf '\n%s\n' 'kotlin { jvmToolchain(17) }' >> "$appkts"
fi

cat > .github/workflows/build.yml <<'YML'
name: Build Auto-Math-Tapper
on:
  push:
    branches: [ main, 'fix/ocr-mlkit' ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout repository
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Set up Android SDK
      uses: android-actions/setup-android@v3

    - name: Build APK
      run: ./gradlew --no-daemon clean assembleDebug

    - name: Upload APK artifact
      uses: actions/upload-artifact@v4
      with:
        name: Auto-Math-Tapper-APK
        path: app/build/outputs/apk/debug/app-debug.apk
YML

./gradlew --no-daemon clean assembleDebug || true

git add -A
git commit -m "fix: ML Kit OCR, accessibility config, projection FG service; SDK 34; CI workflow" || true
git push -u origin "$branch" || true

echo "Done. Open a PR from $branch to main, then check Actions artifacts for the APK."
