package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host Accessibility Service.
 * Provides OS-level multi-touch gesture injection and global system navigation
 * (Back, Home, Recents, Notifications, Lock Screen) for unrooted Android devices.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RemoteAccessibility"
        private var instance: RemoteAccessibilityService? = null

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun getInstance(): RemoteAccessibilityService? = instance

        fun isAvailable(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "RemoteAccessibilityService connected and ready for gesture dispatch.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op for remote desktop
    }

    override fun onInterrupt() {
        Log.w(TAG, "RemoteAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceConnected.value = false
        }
    }

    /**
     * Injects a tap gesture at the given absolute screen coordinates.
     */
    fun injectTap(absX: Float, absY: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(absX, absY)
            lineTo(absX, absY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Injects a long press gesture.
     */
    fun injectLongPress(absX: Float, absY: Float, durationMs: Long = 600, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(absX, absY)
            lineTo(absX, absY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(400, 2000))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Injects a drag / swipe gesture from (startX, startY) to (endX, endY).
     */
    fun injectSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 250,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50, 1000))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Injects a 2-finger pinch gesture (zoom in / zoom out).
     */
    fun injectPinch(
        centerX: Float,
        centerY: Float,
        scale: Float,
        span: Float = 300f,
        durationMs: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val halfSpan = span / 2f
        val startDistance = halfSpan
        val endDistance = (halfSpan * scale).coerceIn(40f, 600f)

        val path1 = Path().apply {
            moveTo(centerX - startDistance, centerY)
            lineTo(centerX - endDistance, centerY)
        }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, durationMs)

        val path2 = Path().apply {
            moveTo(centerX + startDistance, centerY)
            lineTo(centerX + endDistance, centerY)
        }
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, durationMs)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Performs standard Android global system actions.
     */
    fun performSystemKey(keyCode: String): Boolean {
        return when (keyCode.uppercase()) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "LOCK_SCREEN", "POWER" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
            }
            else -> false
        }
    }
}
