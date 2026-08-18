@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource

object CronetUtil {
    /**
     * Kept as a compatibility shim for callers from older builds. The player does not
     * install Cronet at app startup because the provider can be unavailable on TVs and
     * some IPTV servers reject Cronet's request profile.
     */
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) = Unit

    /** Use the predictable Media3 HTTP stack for every stream. */
    fun getDataSourceFactory(): DataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.0")
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)
}
