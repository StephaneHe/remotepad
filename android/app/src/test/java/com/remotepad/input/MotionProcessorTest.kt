package com.remotepad.input

import com.remotepad.viewmodel.PreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MotionProcessorTest {

    private lateinit var prefs: FakePreferencesStore
    private lateinit var settings: SettingsRepository
    private lateinit var processor: MotionProcessor

    @Before
    fun setUp() {
        prefs = FakePreferencesStore()
        settings = SettingsRepository(prefs)
        processor = MotionProcessor(settings)
    }

    // -- Sensitivity 1x (default) --------------------------------------------

    @Test
    fun `sensitivity 1x leaves move unchanged`() {
        settings.mouseSensitivity = 1.0f
        val (dx, dy) = processor.processMouseMove(10f, -5f)
        assertEquals(10, dx)
        assertEquals(-5, dy)
    }

    // -- Sensitivity 2x ------------------------------------------------------

    @Test
    fun `sensitivity 2x doubles move`() {
        settings.mouseSensitivity = 2.0f
        val (dx, dy) = processor.processMouseMove(10f, -5f)
        assertEquals(20, dx)
        assertEquals(-10, dy)
    }

    // -- Sensitivity 0.5x ----------------------------------------------------

    @Test
    fun `sensitivity 0_5x halves move`() {
        settings.mouseSensitivity = 0.5f
        val (dx, dy) = processor.processMouseMove(10f, -4f)
        assertEquals(5, dx)
        assertEquals(-2, dy)
    }

    // -- Scroll sensitivity ---------------------------------------------------

    @Test
    fun `scroll sensitivity applied independently`() {
        settings.scrollSensitivity = 2.0f
        settings.mouseSensitivity = 1.0f

        val (sdx, sdy) = processor.processScroll(0f, 10f)
        assertEquals(0, sdx)
        assertEquals(20, sdy)

        // Mouse should not be affected by scroll sensitivity
        val (mdx, mdy) = processor.processMouseMove(10f, 0f)
        assertEquals(10, mdx)
    }

    // -- Acceleration OFF -----------------------------------------------------

    @Test
    fun `acceleration off no amplification`() {
        settings.accelerationEnabled = false
        settings.mouseSensitivity = 1.0f

        // Even with fast movement, no amplification
        val (dx, dy) = processor.processMouseMove(50f, 0f)
        assertEquals(50, dx)
        assertEquals(0, dy)
    }

    // -- Acceleration ON slow movement ----------------------------------------

    @Test
    fun `acceleration on slow move not amplified`() {
        settings.accelerationEnabled = true
        settings.mouseSensitivity = 1.0f

        // speed = sqrt(5^2 + 5^2) ≈ 7.07, below threshold of 15
        val (dx, dy) = processor.processMouseMove(5f, 5f)
        assertEquals(5, dx)
        assertEquals(5, dy)
    }

    // -- Acceleration ON fast movement ----------------------------------------

    @Test
    fun `acceleration on fast move amplified`() {
        settings.accelerationEnabled = true
        settings.mouseSensitivity = 1.0f

        // speed = sqrt(30^2 + 0^2) = 30, above threshold of 15
        // factor = 1.0 + (30 / 15) = 3.0
        // dx_final = 30 * 1.0 * 3.0 = 90
        val (dx, dy) = processor.processMouseMove(30f, 0f)
        assertEquals(90, dx)
        assertEquals(0, dy)
    }

    // -- Settings defaults ----------------------------------------------------

    @Test
    fun `settings defaults are correct`() {
        assertEquals(1.0f, settings.mouseSensitivity, 0.001f)
        assertEquals(1.0f, settings.scrollSensitivity, 0.001f)
        assertFalse(settings.accelerationEnabled)
        assertTrue(settings.hapticEnabled)
    }

    // -- Settings persistence -------------------------------------------------

    @Test
    fun `settings persisted and restored`() {
        settings.mouseSensitivity = 2.5f
        settings.scrollSensitivity = 0.7f
        settings.accelerationEnabled = true
        settings.hapticEnabled = false

        // Create a new SettingsRepository with the same prefs store
        val restored = SettingsRepository(prefs)
        assertEquals(2.5f, restored.mouseSensitivity, 0.001f)
        assertEquals(0.7f, restored.scrollSensitivity, 0.001f)
        assertTrue(restored.accelerationEnabled)
        assertFalse(restored.hapticEnabled)
    }

    // -- Fake -----------------------------------------------------------------

    private class FakePreferencesStore : PreferencesStore {
        private val data = mutableMapOf<String, String>()
        override fun getString(key: String, default: String): String = data[key] ?: default
        override fun putString(key: String, value: String) { data[key] = value }
    }
}
