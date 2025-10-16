package com.example.automathtapper

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object ProjectionStore {
    @Volatile private var resultCode: Int? = null
    @Volatile private var data: Intent? = null

    fun set(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.data = data
    }

    fun hasProjection(): Boolean = resultCode != null && data != null

    fun getProjection(ctx: Context): MediaProjection? {
        val rc = resultCode ?: return null
        val dt = data ?: return null
        val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.getMediaProjection(rc, dt)
    }
}
