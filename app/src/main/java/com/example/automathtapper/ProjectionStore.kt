package com.example.automathtapper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import java.util.concurrent.CopyOnWriteArrayList

object ProjectionStore {
    private var mpMgr: MediaProjectionManager? = null
    @Volatile private var projection: MediaProjection? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    const val REQ_CODE = 9001

    fun init(context: Context) {
        if (mpMgr == null) {
            mpMgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }
    }

    fun hasProjection(): Boolean = projection != null
    fun getProjection(): MediaProjection? = projection

    fun request(activity: Activity) {
        val mgr = mpMgr ?: run { init(activity); mpMgr!! }
        activity.startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CODE)
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val mgr = mpMgr ?: return
        if (resultCode == Activity.RESULT_OK && data != null) {
            projection?.stop()
            projection = mgr.getMediaProjection(resultCode, data)
            listeners.forEach { it.invoke() }
            listeners.clear()
        }
    }

    fun onReady(callback: () -> Unit) {
        if (projection != null) callback() else listeners += callback
    }

    fun clear() {
        projection?.stop()
        projection = null
        listeners.clear()
    }
}
