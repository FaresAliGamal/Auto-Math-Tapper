package com.example.automathtapper

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

object ErrorOverlay {
    private var view: View? = null
    private var wm: WindowManager? = null
    fun init(ctx: Context) {
        if (wm == null) wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (view == null && canDraw(ctx)) {
            val inflater = LayoutInflater.from(ctx)
            val tv = TextView(ctx)
            tv.text = ""
            tv.textSize = 12f
            tv.setPadding(16, 16, 16, 16)
            tv.setBackgroundColor(0xAA000000.toInt())
            tv.setTextColor(0xFFFFFFFF.toInt())
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 24
            lp.y = 120
            view = tv
            wm?.addView(view, lp)
        }
    }
    fun show(msg: String) {
        try {
            init(App.instance)
            (view as? TextView)?.text = msg
        } catch (_: Throwable) {}
    }
    private fun canDraw(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true
}
