package com.example.automathtapper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class RequestProjectionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProjectionStore.init(this)
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = mgr.createScreenCaptureIntent()
        startActivityForResult(captureIntent, ProjectionStore.REQ_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ProjectionStore.REQ_CODE) {
            try {
                ProjectionStore.onActivityResult(resultCode, data)
            } catch (e: Throwable) {
                ErrorBus.post("ProjectionError: ${e.message}")
            }
        }
        finish()
    }
}
