package com.brentvatne.exoplayer

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.conviva.sdk.ConvivaAnalytics
import com.conviva.sdk.ConvivaSdkConstants
import com.conviva.sdk.ConvivaVideoAnalytics
import java.lang.ref.WeakReference

class ConvivaManager(
    private val context: Context,
    customerKey: String,
    gatewayUrl: String?,
    playerName: String,
    tags: Map<String, Any>,
    private val enableDebug: Boolean
) {
    private val TAG = "ConvivaManager"
    private var activeAnalyticsSession: ConvivaVideoAnalytics? = null
    private val tagMap: MutableMap<String, Any> = HashMap()
    private var exoPlayer: WeakReference<ExoPlayer?>? = null

    init {
        val settings: MutableMap<String, Any> = HashMap()
        settings[ConvivaSdkConstants.LOG_LEVEL] =
            if (enableDebug) ConvivaSdkConstants.LogLevel.DEBUG else ConvivaSdkConstants.LogLevel.NONE

        gatewayUrl?.let {
            if (enableDebug) {
                Log.d(TAG, "Setting Conviva gateway to $it")
            }
            settings[ConvivaSdkConstants.GATEWAY_URL] = it
        }
        ConvivaAnalytics.init(context.applicationContext, customerKey, settings)
        tagMap[ConvivaSdkConstants.PLAYER_NAME] = playerName
        if (tags.isNotEmpty()) {
            tagMap.putAll(tags)
        }
    }

    fun setPlayer(player: ExoPlayer) {
        activeAnalyticsSession?.apply {
            exoPlayer?.let {
                if (enableDebug) {
                    Log.d(TAG, "Player WeakReference cleared: $it")
                }
                it.clear()
                exoPlayer = null
            }
            if (enableDebug) {
                Log.d(TAG, "Player set: $player")
            }
            val info: MutableMap<String, Any> = java.util.HashMap()
            info["Conviva.Module"] = ConvivaSdkConstants.ConvivaModule.ExoPlayer3
            activeAnalyticsSession?.setPlayer(player, info)

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        reportPlaybackEnded()
                    }
                }
            })
        } ?: run {
            if (enableDebug) {
                Log.d(TAG, "Player WeakReference set: $player")
            }
            exoPlayer = WeakReference(player)
        }
    }

    fun reportPlaybackRequested(assetName: String, isLive: Boolean, newTags: Map<String, Any>?) {
        if (enableDebug) {
            Log.d(TAG, "Call to reportPlaybackRequested with $assetName, $isLive")
        }
        if (activeAnalyticsSession != null) {
            Log.e(TAG, "Previous session still active - session corruption WILL occur")
        }
        activeAnalyticsSession = ConvivaAnalytics.buildVideoAnalytics(context)
        tagMap[ConvivaSdkConstants.ASSET_NAME] = assetName
        tagMap[ConvivaSdkConstants.IS_LIVE] = isLive.toString()

        if (!newTags.isNullOrEmpty()) {
            tagMap.putAll(newTags)
        }
        activeAnalyticsSession?.reportPlaybackRequested(tagMap)
    }

    fun setPlaybackData(streamUrl: String?, viewerId: String, newTags: Map<String, Any>?) {
        if (enableDebug) {
            Log.d(TAG, "Call to setPlaybackData with $streamUrl, $newTags")
        }
        if (!streamUrl.isNullOrEmpty()) {
            tagMap[ConvivaSdkConstants.STREAM_URL] = streamUrl
        }
        tagMap[ConvivaSdkConstants.VIEWER_ID] = viewerId
        if (!newTags.isNullOrEmpty()) {
            tagMap.putAll(newTags)
        }
        activeAnalyticsSession?.setContentInfo(tagMap)
        exoPlayer?.get()?.let {
            if (enableDebug) {
                Log.d(TAG, "Player WeakReference used: $it")
            }
            setPlayer(it)
        }
    }

    fun reportWarning(message: String) {
        if (enableDebug) {
            Log.d(TAG, "Call to reportWarning with $message")
        }
        activeAnalyticsSession?.reportPlaybackError(message, ConvivaSdkConstants.ErrorSeverity.WARNING)
    }

    fun reportError(message: String, newTags: MutableMap<String, Any>?) {
        if (enableDebug) {
            Log.d(TAG, "Call to reportError with $message")
        }
        if (!newTags.isNullOrEmpty()) {
            tagMap.putAll(newTags)
            activeAnalyticsSession?.setContentInfo(tagMap)
        }

        activeAnalyticsSession?.reportPlaybackError(message, ConvivaSdkConstants.ErrorSeverity.FATAL)
        end()
    }

    fun reportPlaybackEnded() {
        if (enableDebug) {
            Log.d(TAG, "Call to reportPlaybackEnded")
        }
        activeAnalyticsSession?.reportPlaybackEnded()
        end()
    }

    private fun end() {
        activeAnalyticsSession?.let {
            if (enableDebug) {
                Log.d(TAG, "Ending session")
            }
            activeAnalyticsSession?.release()
            activeAnalyticsSession = null
        } ?: run {
            if (enableDebug) {
                Log.d(TAG, "No session to end")
            }
        }
    }
}
