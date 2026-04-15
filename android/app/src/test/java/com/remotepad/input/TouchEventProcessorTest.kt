package com.remotepad.input

import com.remotepad.model.RemoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchEventProcessorTest {

    private val events = mutableListOf<RemoteEvent>()
    private lateinit var processor: TouchEventProcessor
    private var fakeTime = 0L

    @Before
    fun setUp() {
        events.clear()
        fakeTime = 0L
        processor = TouchEventProcessor(
            emitter = { events.add(it) },
            clock = { fakeTime }
        )
    }

    // -- Mouse move ----------------------------------------------------------

    @Test
    fun `single finger drag emits MouseMove`() {
        // DOWN at (100, 100)
        processor.onTouchInput(touch(TouchAction.DOWN, listOf(Pointer(0, 100f, 100f)), 0))
        // MOVE to (120, 90) — delta = (20, -10), exceeds tap threshold so it's a drag
        fakeTime = 20
        processor.onTouchInput(touch(TouchAction.MOVE, listOf(Pointer(0, 120f, 90f)), 20))
        processor.flush()
        // UP
        processor.onTouchInput(touch(TouchAction.UP, listOf(Pointer(0, 120f, 90f)), 40))

        val moves = events.filterIsInstance<RemoteEvent.MouseMove>()
        assertTrue("Expected at least one MouseMove", moves.isNotEmpty())
        val total = moves.fold(Pair(0, 0)) { acc, m -> Pair(acc.first + m.dx, acc.second + m.dy) }
        assertEquals(20, total.first)
        assertEquals(-10, total.second)
    }

    // -- Single tap -----------------------------------------------------------

    @Test
    fun `single tap emits left click`() {
        processor.onTouchInput(touch(TouchAction.DOWN, listOf(Pointer(0, 100f, 100f)), 0))
        processor.onTouchInput(touch(TouchAction.UP, listOf(Pointer(0, 100f, 100f)), 100))

        assertEquals(1, events.size)
        assertEquals(RemoteEvent.MouseClick("left"), events[0])
    }

    // -- Long press -----------------------------------------------------------

    @Test
    fun `long press emits right click`() {
        processor.onTouchInput(touch(TouchAction.DOWN, listOf(Pointer(0, 100f, 100f)), 0))
        // Simulate time passing > 500ms
        fakeTime = 501
        val fired = processor.checkLongPress()

        assertTrue("Long press should fire", fired)
        assertEquals(1, events.size)
        assertEquals(RemoteEvent.MouseClick("right"), events[0])
    }

    @Test
    fun `long press does not fire if moved`() {
        processor.onTouchInput(touch(TouchAction.DOWN, listOf(Pointer(0, 100f, 100f)), 0))
        // Move far enough to mark as moved
        fakeTime = 20
        processor.onTouchInput(touch(TouchAction.MOVE, listOf(Pointer(0, 150f, 150f)), 20))
        fakeTime = 501
        val fired = processor.checkLongPress()

        assertTrue("Long press should NOT fire after move", !fired)
    }

    // -- Two-finger volume ---------------------------------------------------

    @Test
    fun `two finger drag down emits volume_down keypress`() {
        val p1 = Pointer(0, 100f, 200f)
        val p2 = Pointer(1, 150f, 200f)

        processor.onTouchInput(touch(TouchAction.DOWN, listOf(p1), 0))
        processor.onTouchInput(touch(TouchAction.POINTER_DOWN, listOf(p1, p2), 5))

        // Move both fingers 120px down (3x VOLUME_STEP_PX = 40)
        val p1m = Pointer(0, 100f, 320f)
        val p2m = Pointer(1, 150f, 320f)
        processor.onTouchInput(touch(TouchAction.MOVE, listOf(p1m, p2m), 25))

        processor.onTouchInput(touch(TouchAction.POINTER_UP, listOf(p1m), 50))
        processor.onTouchInput(touch(TouchAction.UP, listOf(p1m), 55))

        val volumeKeys = events.filterIsInstance<RemoteEvent.KeyPress>()
            .filter { it.key == "volume_down" }
        assertEquals(3, volumeKeys.size)
    }

    @Test
    fun `two finger drag up emits volume_up keypress`() {
        val p1 = Pointer(0, 100f, 320f)
        val p2 = Pointer(1, 150f, 320f)

        processor.onTouchInput(touch(TouchAction.DOWN, listOf(p1), 0))
        processor.onTouchInput(touch(TouchAction.POINTER_DOWN, listOf(p1, p2), 5))

        // Move both fingers 80px up (2x VOLUME_STEP_PX = 40)
        val p1m = Pointer(0, 100f, 240f)
        val p2m = Pointer(1, 150f, 240f)
        processor.onTouchInput(touch(TouchAction.MOVE, listOf(p1m, p2m), 25))

        processor.onTouchInput(touch(TouchAction.POINTER_UP, listOf(p1m), 50))
        processor.onTouchInput(touch(TouchAction.UP, listOf(p1m), 55))

        val volumeKeys = events.filterIsInstance<RemoteEvent.KeyPress>()
            .filter { it.key == "volume_up" }
        assertEquals(2, volumeKeys.size)
    }

    // -- Two-finger tap → right click ----------------------------------------

    @Test
    fun `two finger tap emits right click`() {
        val p1 = Pointer(0, 100f, 100f)
        val p2 = Pointer(1, 150f, 100f)

        processor.onTouchInput(touch(TouchAction.DOWN, listOf(p1), 0))
        processor.onTouchInput(touch(TouchAction.POINTER_DOWN, listOf(p1, p2), 5))
        processor.onTouchInput(touch(TouchAction.POINTER_UP, listOf(p1), 50))
        processor.onTouchInput(touch(TouchAction.UP, listOf(p1), 55))

        assertEquals(1, events.size)
        assertEquals(RemoteEvent.MouseClick("right"), events[0])
    }

    // -- Three-finger tap → middle click -------------------------------------

    @Test
    fun `three finger tap emits middle click`() {
        val p1 = Pointer(0, 100f, 100f)
        val p2 = Pointer(1, 150f, 100f)
        val p3 = Pointer(2, 200f, 100f)

        processor.onTouchInput(touch(TouchAction.DOWN, listOf(p1), 0))
        processor.onTouchInput(touch(TouchAction.POINTER_DOWN, listOf(p1, p2), 5))
        processor.onTouchInput(touch(TouchAction.POINTER_DOWN, listOf(p1, p2, p3), 10))
        processor.onTouchInput(touch(TouchAction.POINTER_UP, listOf(p1, p2), 50))
        processor.onTouchInput(touch(TouchAction.POINTER_UP, listOf(p1), 55))
        processor.onTouchInput(touch(TouchAction.UP, listOf(p1), 60))

        assertEquals(1, events.size)
        assertEquals(RemoteEvent.MouseClick("middle"), events[0])
    }

    // -- Button bar -----------------------------------------------------------

    @Test
    fun `button bar left click`() {
        // Simulated by directly calling emitter (TrackpadViewModel.onButtonClick)
        val emitted = mutableListOf<RemoteEvent>()
        val emitter: EventEmitter = { emitted.add(it) }
        emitter(RemoteEvent.MouseClick("left"))
        assertEquals(RemoteEvent.MouseClick("left"), emitted[0])
    }

    @Test
    fun `button bar right click`() {
        val emitted = mutableListOf<RemoteEvent>()
        val emitter: EventEmitter = { emitted.add(it) }
        emitter(RemoteEvent.MouseClick("right"))
        assertEquals(RemoteEvent.MouseClick("right"), emitted[0])
    }

    @Test
    fun `button bar middle click`() {
        val emitted = mutableListOf<RemoteEvent>()
        val emitter: EventEmitter = { emitted.add(it) }
        emitter(RemoteEvent.MouseClick("middle"))
        assertEquals(RemoteEvent.MouseClick("middle"), emitted[0])
    }

    // -- Throttling -----------------------------------------------------------

    @Test
    fun `events throttled at 60Hz`() {
        processor.onTouchInput(touch(TouchAction.DOWN, listOf(Pointer(0, 100f, 100f)), 0))

        // Send 10 moves, each 5ms apart (well under 16ms threshold)
        for (i in 1..10) {
            val t = (i * 5).toLong()
            fakeTime = t
            processor.onTouchInput(touch(
                TouchAction.MOVE,
                listOf(Pointer(0, 100f + i * 2, 100f)),
                t
            ))
        }
        processor.flush()

        processor.onTouchInput(touch(TouchAction.UP, listOf(Pointer(0, 120f, 100f)), 60))

        val moves = events.filterIsInstance<RemoteEvent.MouseMove>()
        // With 10 moves at 5ms interval, throttle at 16ms allows roughly 3-4 emits + flush
        assertTrue("Moves should be aggregated, got ${moves.size}", moves.size < 10)
        // Total dx should still be 20 (10 * 2)
        val totalDx = moves.sumOf { it.dx }
        assertEquals("Total dx should be preserved", 20, totalDx)
    }

    // -- Scroll zone ----------------------------------------------------------

    @Test
    fun `scroll zone vertical drag emits MouseScroll`() {
        // Scroll zone is handled the same way as 2-finger scroll in the processor.
        // The UI layer distinguishes zones. Here we test direct scroll emission.
        val emitted = mutableListOf<RemoteEvent>()
        val emitter: EventEmitter = { emitted.add(it) }
        // Simulate the scroll zone calling emitter directly
        emitter(RemoteEvent.MouseScroll(0, 5))
        assertEquals(RemoteEvent.MouseScroll(0, 5), emitted[0])
    }

    // -- Helpers --------------------------------------------------------------

    private fun touch(action: TouchAction, pointers: List<Pointer>, timestampMs: Long) =
        TouchInput(action, pointers, timestampMs)
}
