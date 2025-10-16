package com.example.automathtapper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object ProjectionStore {
    private var mpMgr: MediaProjectionManager? = null
    private var projection: MediaProjection? = null

    const val REQ_CODE = 9001

    fun init(context: Context) {
        if (mpMgr == null) {
            mpMgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }
    }

    fun hasProjection(): Boolean = projection != null

    fun request(activity: Activity) {
        val mgr = mpMgr ?: run {
            init(activity)
            mpMgr!!
        }
        val intent = mgr.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQ_CODE)
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val mgr = mpMgr ?: return
        if (resultCode == Activity.RESULT_OK && data != null) {
            projection = mgr.getMediaProjection(resultCode, data)
        }
    }

    fun getProjection(context: Context): MediaProjection? {
        if (mpMgr == null) init(context)
        return projection
    }

    fun clear() {
        projection?.stop()
        projection = null
    }
}
