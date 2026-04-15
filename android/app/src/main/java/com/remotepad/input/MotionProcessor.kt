package com.remotepad.input

import com.remotepad.viewmodel.PreferencesStore
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Stores and exposes user-configurable settings for motion and haptics.
 */
class SettingsRepository(private val prefs: PreferencesStore) {

    companion object {
        const val KEY_MOUSE_SENSITIVITY = "mouse_sensitivity"
        const val KEY_SCROLL_SENSITIVITY = "scroll_sensitivity"
        const val KEY_ACCELERATION = "acceleration_enabled"
        const val KEY_HAPTIC = "haptic_enabled"

        const val DEFAULT_MOUSE_SENSITIVITY = 1.0f
        const val DEFAULT_SCROLL_SENSITIVITY = 1.0f
        const val DEFAULT_ACCELERATION = false
        const val DEFAULT_HAPTIC = true
    }

    var mouseSensitivity: Float
        get() = prefs.getString(KEY_MOUSE_SENSITIVITY, DEFAULT_MOUSE_SENSITIVITY.toString()).toFloatOrNull()
            ?: DEFAULT_MOUSE_SENSITIVITY
        set(value) = prefs.putString(KEY_MOUSE_SENSITIVITY, value.coerceIn(0.5f, 3.0f).toString())

    var scrollSensitivity: Float
        get() = prefs.getString(KEY_SCROLL_SENSITIVITY, DEFAULT_SCROLL_SENSITIVITY.toString()).toFloatOrNull()
            ?: DEFAULT_SCROLL_SENSITIVITY
        set(value) = prefs.putString(KEY_SCROLL_SENSITIVITY, value.coerceIn(0.5f, 3.0f).toString())

    var accelerationEnabled: Boolean
        get() = prefs.getString(KEY_ACCELERATION, DEFAULT_ACCELERATION.toString()).toBooleanStrictOrNull()
            ?: DEFAULT_ACCELERATION
        set(value) = prefs.putString(KEY_ACCELERATION, value.toString())

    var hapticEnabled: Boolean
        get() = prefs.getString(KEY_HAPTIC, DEFAULT_HAPTIC.toString()).toBooleanStrictOrNull()
            ?: DEFAULT_HAPTIC
        set(value) = prefs.putString(KEY_HAPTIC, value.toString())
}

/**
 * Applies sensitivity and acceleration to raw motion values.
 */
class MotionProcessor(private val settings: SettingsRepository) {

    companion object {
        /** Speed threshold (px) above which acceleration kicks in. */
        const val ACCELERATION_THRESHOLD = 15f
    }

    /**
     * Process raw mouse move deltas, applying sensitivity and optional acceleration.
     * Returns the final (dx, dy) pair as integers.
     */
    fun processMouseMove(rawDx: Float, rawDy: Float): Pair<Int, Int> {
        val sensitivity = settings.mouseSensitivity
        val factor = if (settings.accelerationEnabled) {
            accelerationFactor(rawDx, rawDy)
        } else {
            1.0f
        }
        val dx = (rawDx * sensitivity * factor).roundToInt()
        val dy = (rawDy * sensitivity * factor).roundToInt()
        return Pair(dx, dy)
    }

    /**
     * Process raw scroll delta, applying scroll sensitivity.
     * Returns the final dy as integer.
     */
    fun processScroll(rawDx: Float, rawDy: Float): Pair<Int, Int> {
        val sensitivity = settings.scrollSensitivity
        return Pair(
            (rawDx * sensitivity).roundToInt(),
            (rawDy * sensitivity).roundToInt()
        )
    }

    /**
     * Pure acceleration curve: amplifies fast movements.
     */
    fun accelerationFactor(rawDx: Float, rawDy: Float): Float {
        val speed = sqrt(rawDx * rawDx + rawDy * rawDy)
        return if (speed > ACCELERATION_THRESHOLD) {
            1.0f + (speed / ACCELERATION_THRESHOLD)
        } else {
            1.0f
        }
    }
}
