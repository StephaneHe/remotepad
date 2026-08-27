package com.remotepad.input

import com.remotepad.model.RemoteEvent

/**
 * Simple representation of a touch pointer.
 */
data class Pointer(val id: Int, val x: Float, val y: Float)

/**
 * Touch actions mirroring MotionEvent but decoupled from Android SDK.
 */
enum class TouchAction {
    DOWN, MOVE, UP, POINTER_DOWN, POINTER_UP
}

/**
 * A touch event containing action, timestamp, and active pointers.
 */
data class TouchInput(
    val action: TouchAction,
    val pointers: List<Pointer>,
    val timestampMs: Long
)

/**
 * Callback for emitted remote events.
 */
typealias EventEmitter = (RemoteEvent) -> Unit

/**
 * Transforms raw touch inputs into [RemoteEvent] instances.
 *
 * Handles:
 * - 1-finger drag → MouseMove
 * - 1-finger tap (< 200ms, < 10px) → MouseClick("left")
 * - 1-finger long press (> 500ms) → MouseClick("right")
 * - 2-finger drag → MouseScroll
 * - 2-finger tap → MouseClick("right")
 * - 3-finger tap → MouseClick("middle")
 * - Throttling at 60 Hz (16ms interval)
 */
class TouchEventProcessor(
    private val emitter: EventEmitter,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val TAP_MAX_DURATION_MS = 200L
        const val TAP_MAX_DISTANCE_PX = 10f
        const val LONG_PRESS_MIN_MS = 500L
        const val THROTTLE_INTERVAL_MS = 16L
        const val VOLUME_STEP_PX = 40f
        const val ZOOM_STEP_PX = 30f
    }

    // Tracking state
    private var downTime: Long = 0L
    private var downPointers: List<Pointer> = emptyList()
    private var lastPointers: List<Pointer> = emptyList()
    private var maxFingerCount: Int = 0
    private var moved: Boolean = false
    private var longPressFired: Boolean = false

    // Throttling
    private var lastEmitTime: Long = 0L
    private var pendingDx: Float = 0f
    private var pendingDy: Float = 0f
    private var pendingScrollDx: Float = 0f
    private var pendingScrollDy: Float = 0f

    // Two-finger volume accumulator
    private var volumeAccumulator: Float = 0f

    // Pinch zoom accumulator
    private var pinchAccumulator: Float = 0f
    private var lastSpan: Float = -1f

    /**
     * Feed a touch input into the processor.
     */
    fun onTouchInput(input: TouchInput) {
        when (input.action) {
            TouchAction.DOWN -> handleDown(input)
            TouchAction.POINTER_DOWN -> handlePointerDown(input)
            TouchAction.MOVE -> handleMove(input)
            TouchAction.POINTER_UP -> handlePointerUp(input)
            TouchAction.UP -> handleUp(input)
        }
    }

    /**
     * Call periodically to check for long press.
     * Returns true if a long press was detected and fired.
     */
    fun checkLongPress(): Boolean {
        if (longPressFired || moved || downPointers.isEmpty()) return false
        if (maxFingerCount != 1) return false
        val elapsed = clock() - downTime
        if (elapsed >= LONG_PRESS_MIN_MS) {
            longPressFired = true
            emitter(RemoteEvent.MouseClick("right"))
            return true
        }
        return false
    }

    /**
     * Flush any pending throttled move/scroll events.
     */
    fun flush() {
        flushPendingMove()
        flushPendingScroll()
    }

    // -- Internal handlers --------------------------------------------------

    private fun handleDown(input: TouchInput) {
        downTime = input.timestampMs
        downPointers = input.pointers.toList()
        lastPointers = input.pointers.toList()
        maxFingerCount = input.pointers.size
        moved = false
        longPressFired = false
        pendingDx = 0f
        pendingDy = 0f
        pendingScrollDx = 0f
        pendingScrollDy = 0f
        volumeAccumulator = 0f
        pinchAccumulator = 0f
        lastSpan = -1f
    }

    private fun handlePointerDown(input: TouchInput) {
        lastPointers = input.pointers.toList()
        if (input.pointers.size > maxFingerCount) {
            maxFingerCount = input.pointers.size
        }
    }

    private fun handleMove(input: TouchInput) {
        if (input.pointers.isEmpty() || lastPointers.isEmpty()) return

        val fingerCount = input.pointers.size

        if (fingerCount == 1 && maxFingerCount == 1) {
            // Single finger drag → mouse move
            val prev = lastPointers[0]
            val curr = input.pointers[0]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y

            if (!moved && distance(downPointers[0], curr) > TAP_MAX_DISTANCE_PX) {
                moved = true
            }

            pendingDx += dx
            pendingDy += dy
            throttleEmitMove(input.timestampMs)
        } else if (fingerCount >= 2 && lastPointers.size >= 2) {
            moved = true
            val p0prev = lastPointers[0]
            val p1prev = lastPointers[1]
            val p0curr = input.pointers[0]
            val p1curr = input.pointers[1]

            // Pinch zoom: track span change between the two fingers
            val currSpan = distance(p0curr, p1curr)
            if (lastSpan >= 0f) {
                val dSpan = currSpan - lastSpan
                pinchAccumulator += dSpan
                // Spread → zoom in
                while (pinchAccumulator >= ZOOM_STEP_PX) {
                    emitter(RemoteEvent.Zoom(1))
                    pinchAccumulator -= ZOOM_STEP_PX
                }
                // Squeeze → zoom out
                while (pinchAccumulator <= -ZOOM_STEP_PX) {
                    emitter(RemoteEvent.Zoom(-1))
                    pinchAccumulator += ZOOM_STEP_PX
                }
            }
            lastSpan = currSpan

            // Volume: vertical translation of centroid
            val centroidPrevY = (p0prev.y + p1prev.y) / 2f
            val centroidCurrY = (p0curr.y + p1curr.y) / 2f
            val dy = centroidCurrY - centroidPrevY
            volumeAccumulator += dy
            // Swipe UP (negative dy) → volume UP
            while (volumeAccumulator <= -VOLUME_STEP_PX) {
                emitter(RemoteEvent.KeyPress("volume_up", emptyList()))
                volumeAccumulator += VOLUME_STEP_PX
            }
            // Swipe DOWN (positive dy) → volume DOWN
            while (volumeAccumulator >= VOLUME_STEP_PX) {
                emitter(RemoteEvent.KeyPress("volume_down", emptyList()))
                volumeAccumulator -= VOLUME_STEP_PX
            }
        }

        lastPointers = input.pointers.toList()
    }

    private fun handlePointerUp(input: TouchInput) {
        lastPointers = input.pointers.toList()
    }

    private fun handleUp(input: TouchInput) {
        flushPendingMove()
        flushPendingScroll()

        val duration = input.timestampMs - downTime

        if (!moved && !longPressFired) {
            if (duration < TAP_MAX_DURATION_MS) {
                when (maxFingerCount) {
                    1 -> emitter(RemoteEvent.MouseClick("left"))
                    2 -> emitter(RemoteEvent.MouseClick("right"))
                    3 -> emitter(RemoteEvent.MouseClick("middle"))
                }
            }
        }

        // Reset state
        downPointers = emptyList()
        lastPointers = emptyList()
        maxFingerCount = 0
        moved = false
        longPressFired = false
    }

    // -- Throttling ----------------------------------------------------------

    private fun throttleEmitMove(timestampMs: Long) {
        if (timestampMs - lastEmitTime >= THROTTLE_INTERVAL_MS) {
            flushPendingMove()
            lastEmitTime = timestampMs
        }
    }

    private fun throttleEmitScroll(timestampMs: Long) {
        if (timestampMs - lastEmitTime >= THROTTLE_INTERVAL_MS) {
            flushPendingScroll()
            lastEmitTime = timestampMs
        }
    }

    private fun flushPendingMove() {
        val dx = pendingDx.toInt()
        val dy = pendingDy.toInt()
        if (dx != 0 || dy != 0) {
            emitter(RemoteEvent.MouseMove(dx, dy))
            pendingDx = 0f
            pendingDy = 0f
        }
    }

    private fun flushPendingScroll() {
        val dx = pendingScrollDx.toInt()
        val dy = pendingScrollDy.toInt()
        if (dx != 0 || dy != 0) {
            emitter(RemoteEvent.MouseScroll(dx, dy))
            pendingScrollDx = 0f
            pendingScrollDy = 0f
        }
    }

    private fun distance(a: Pointer, b: Pointer): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
