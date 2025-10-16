package com.example.automathtapper

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object ProjectionStore {
    @Volatile private var dataIntent: Intent? = null
    fun setIntent(data: Intent) { dataIntent = data }
    fun hasProjection(): Boolean = dataIntent != null
    fun getProjection(ctx: Context): MediaProjection? {
        val di = dataIntent ?: return null
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.getMediaProjection(Activity.RESULT_OK, di)
    }
}
