package com.brentvatne.react

import android.util.Log
import com.brentvatne.common.toolbox.ReactBridgeUtils
import com.brentvatne.exoplayer.ConvivaManager
import com.brentvatne.exoplayer.ReactExoplayerView
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import kotlin.math.roundToInt

class VideoManagerModule(reactContext: ReactApplicationContext?) : ReactContextBaseJavaModule(reactContext) {
    private var convivaManager: ConvivaManager? = null

    override fun getName(): String {
        return REACT_CLASS
    }

    private fun performOnPlayerView(reactTag: Int, callback: (ReactExoplayerView?) -> Unit) {
        UiThreadUtil.runOnUiThread {
            try {
                val uiManager = UIManagerHelper.getUIManager(
                    reactApplicationContext,
                    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) UIManagerType.FABRIC else UIManagerType.DEFAULT
                )

                val view = uiManager?.resolveView(reactTag)

                if (view is ReactExoplayerView) {
                    callback(view)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    @ReactMethod
    fun convivaInit(customerKey: String, gatewayUrl: String?, playerName: String, tags: ReadableMap, enableDebug: Boolean, reactTag: Int) {
        performOnPlayerView(reactTag) { exoplayerView ->
            exoplayerView?.let {
                convivaManager = ConvivaManager(exoplayerView.context, customerKey, gatewayUrl, playerName, tags.toHashMap(), enableDebug)
                exoplayerView.setPlayerCreatedCallback { exoPlayer ->
                    convivaManager?.setPlayer(exoPlayer)
                }
            } ?: run {
                Log.e(REACT_CLASS, "Missing view for convivaInit")
            }
        }
    }

    @ReactMethod
    fun reportPlaybackRequested(assetName: String, isLive: Boolean, tags: ReadableMap, reactTag: Int) {
        // Forcing onto UI thread to prevent race condition with init
        UiThreadUtil.runOnUiThread {
            convivaManager?.reportPlaybackRequested(assetName, isLive, tags.toHashMap())
                ?: run {
                    Log.e(REACT_CLASS, "Missing convivaManager for reportPlaybackRequested")
                }
        }
    }

    @ReactMethod
    fun setPlaybackData(streamUrl: String?, viewerId: String, tags: ReadableMap, reactTag: Int) {
        // Forcing onto UI thread to prevent race condition with init
        UiThreadUtil.runOnUiThread {
            convivaManager?.setPlaybackData(streamUrl, viewerId, tags.toHashMap())
                ?: run {
                    Log.e(REACT_CLASS, "Missing convivaManager for setPlaybackData")
                }
        }
    }

    @ReactMethod
    fun reportError(message: String, tags: ReadableMap, reactTag: Int) {
        // Forcing onto UI thread to prevent race condition with init
        UiThreadUtil.runOnUiThread {
            convivaManager?.reportError(message, tags.toHashMap())
                ?: run {
                    Log.e(REACT_CLASS, "Missing convivaManager for reportError")
                }
        }
    }

    @ReactMethod
    fun setPlayerPauseState(paused: Boolean?, reactTag: Int) {
        performOnPlayerView(reactTag) {
            it?.setPausedModifier(paused!!)
        }
    }

    @ReactMethod
    fun seek(info: ReadableMap, reactTag: Int) {
        if (!info.hasKey("time")) {
            return
        }

        val time = ReactBridgeUtils.safeGetInt(info, "time")
        performOnPlayerView(reactTag) {
            it?.seekTo((time * 1000f).roundToInt().toLong())
        }
    }

    companion object {
        private const val REACT_CLASS = "VideoManager"
    }
}
