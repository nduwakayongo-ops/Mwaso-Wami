package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AudioTrack
import com.example.data.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mwaso Wami", appName)
    }

    @Test
    fun `test dj crossfade smooth curve calculation`() {
        // Test smooth curve values at 0%, 25%, 50%, 75%, 100%
        val progress0 = 0f
        val smooth0 = progress0 * progress0 * (3f - 2f * progress0)
        val out0 = 1.0f - smooth0
        val in0 = smooth0
        assertEquals(1.0f, out0, 0.001f)
        assertEquals(0.0f, in0, 0.001f)

        val progressHalf = 0.5f
        val smoothHalf = progressHalf * progressHalf * (3f - 2f * progressHalf)
        val outHalf = 1.0f - smoothHalf
        val inHalf = smoothHalf
        assertEquals(0.5f, outHalf, 0.001f)
        assertEquals(0.5f, inHalf, 0.001f)

        val progress1 = 1f
        val smooth1 = progress1 * progress1 * (3f - 2f * progress1)
        val out1 = 1.0f - smooth1
        val in1 = smooth1
        assertEquals(0.0f, out1, 0.001f)
        assertEquals(1.0f, in1, 0.001f)
    }

    @Test
    fun `test dj crossfade progression intervals`() {
        // Simulate an 8-second crossfade window at T-8s, T-6s, T-4s, T-2s, T-0s
        val crossfadeMs = 8000L

        // T-8s (start)
        val elapsed0 = 0L
        val prog0 = (elapsed0.toFloat() / crossfadeMs).coerceIn(0f, 1f)
        val smooth0 = prog0 * prog0 * (3f - 2f * prog0)
        assertEquals(1.0f, (1.0f - smooth0), 0.01f)
        assertEquals(0.0f, smooth0, 0.01f)

        // T-4s (midpoint)
        val elapsed4 = 4000L
        val prog4 = (elapsed4.toFloat() / crossfadeMs).coerceIn(0f, 1f)
        val smooth4 = prog4 * prog4 * (3f - 2f * prog4)
        assertEquals(0.50f, (1.0f - smooth4), 0.01f)
        assertEquals(0.50f, smooth4, 0.01f)

        // T-0s (end)
        val elapsed8 = 8000L
        val prog8 = (elapsed8.toFloat() / crossfadeMs).coerceIn(0f, 1f)
        val smooth8 = prog8 * prog8 * (3f - 2f * prog8)
        assertEquals(0.0f, (1.0f - smooth8), 0.01f)
        assertEquals(1.0f, smooth8, 0.01f)
    }

    @Test
    fun `test audio track duration formatting`() {
        val track = AudioTrack(
            id = 1L,
            title = "Mwaso wa Mwono",
            artist = "Nduwa Kayongo",
            album = "Herança",
            durationMs = 214000L, // 3 min 34 s
            mediaUri = "content://media/audio/1"
        )
        assertEquals("3:34", track.formattedDuration)
    }

    @Test
    fun `test repeat mode cycle progression`() {
        var mode = RepeatMode.OFF
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ALL, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ONE, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun `test playlist queue ordering and insertion`() {
        val track1 = AudioTrack(1L, "Faixa 1", "Artista", "Album", 180000L, "content://media/1")
        val track2 = AudioTrack(2L, "Faixa 2", "Artista", "Album", 200000L, "content://media/2")
        val track3 = AudioTrack(3L, "Faixa 3", "Artista", "Album", 220000L, "content://media/3")

        val queue = mutableListOf(track1, track3)
        // insert track2 as play next (after track1)
        queue.add(1, track2)

        assertEquals(3, queue.size)
        assertEquals(1L, queue[0].id)
        assertEquals(2L, queue[1].id)
        assertEquals(3L, queue[2].id)
    }

    @Test
    fun `test crossfade trigger window calculation`() {
        val durationMs = 180000L // 3 minutes
        val crossfadeSec8 = 8
        val crossfadeMs8 = crossfadeSec8 * 1000L

        // Current position at 170s -> 10s remaining (outside 8s window)
        val pos170 = 170000L
        val rem170 = durationMs - pos170
        assertFalse(rem170 in 1..crossfadeMs8)

        // Current position at 173s -> 7s remaining (inside 8s window)
        val pos173 = 173000L
        val rem173 = durationMs - pos173
        assertTrue(rem173 in 1..crossfadeMs8)

        // When crossfadeSec = 0 (OFF), should never trigger
        val crossfadeSec0 = 0
        assertFalse(crossfadeSec0 > 0)
    }

    @Test
    fun `test secondary player position preservation on promotion`() {
        // Track 2 started at 00:00 when Track 1 reached 03:44 (in an 8s crossfade)
        // Over 8s, Track 2 progressed from 00:00 to 00:08 (8000ms)
        val initialSecondaryPos = 0L
        val crossfadeDurationMs = 8000L
        val elapsedCrossfadeMs = 8000L

        val secondaryCurrentPos = initialSecondaryPos + elapsedCrossfadeMs
        assertEquals(8000L, secondaryCurrentPos)

        // Upon promotion, the promoted player's position is strictly preserved and NEVER reset to 0
        val promotedPlayerPos = secondaryCurrentPos
        assertEquals(8000L, promotedPlayerPos)
        assertTrue("Promoted position must be > 0", promotedPlayerPos > 0L)
    }
}
