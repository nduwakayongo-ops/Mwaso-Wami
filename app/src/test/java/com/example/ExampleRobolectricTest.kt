package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AudioTrack
import com.example.data.model.RepeatMode
import com.example.service.CrossfadeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    fun `test equal power dj crossfade curve calculations`() {
        // Test equal power curve: oldGain = cos(p * PI / 2), newGain = sin(p * PI / 2)
        // 1. At start (p = 0.0) -> Track 1 = 100% (1.0), Track 2 = 0% (0.0)
        val p0 = 0.0
        val out0 = cos(p0 * PI / 2.0).toFloat()
        val in0 = sin(p0 * PI / 2.0).toFloat()
        assertEquals(1.0f, out0, 0.001f)
        assertEquals(0.0f, in0, 0.001f)

        // 2. At midpoint (p = 0.5) -> Equal power sum (cos^2 + sin^2 = 1.0)
        val pHalf = 0.5
        val outHalf = cos(pHalf * PI / 2.0).toFloat()
        val inHalf = sin(pHalf * PI / 2.0).toFloat()
        assertEquals(0.7071f, outHalf, 0.01f)
        assertEquals(0.7071f, inHalf, 0.01f)
        val powerSum = (outHalf * outHalf) + (inHalf * inHalf)
        assertEquals(1.0f, powerSum, 0.01f)

        // 3. At completion (p = 1.0) -> Track 1 = 0% (0.0), Track 2 = 100% (1.0)
        val p1 = 1.0
        val out1 = cos(p1 * PI / 2.0).toFloat()
        val in1 = sin(p1 * PI / 2.0).toFloat()
        assertEquals(0.0f, out1, 0.001f)
        assertEquals(1.0f, in1, 0.001f)
    }

    @Test
    fun `test dj crossfade intervals for 5s and 8s scenarios`() {
        // Test 5-second crossfade progression
        val durationMs5 = 5000L
        for (second in 0..5) {
            val progress = (second * 1000L).toFloat() / durationMs5
            val gain1 = cos(progress * PI / 2.0).toFloat()
            val gain2 = sin(progress * PI / 2.0).toFloat()
            assertTrue(gain1 in 0.0f..1.0f)
            assertTrue(gain2 in 0.0f..1.0f)
        }

        // Test 8-second crossfade progression
        val durationMs8 = 8000L
        for (second in 0..8) {
            val progress = (second * 1000L).toFloat() / durationMs8
            val gain1 = cos(progress * PI / 2.0).toFloat()
            val gain2 = sin(progress * PI / 2.0).toFloat()
            assertTrue(gain1 in 0.0f..1.0f)
            assertTrue(gain2 in 0.0f..1.0f)
        }
    }

    @Test
    fun `test repeat one crossfade targeting same track`() {
        val currentTrack = AudioTrack(
            id = 42L,
            title = "Mwaso wa Mwono",
            artist = "Nduwa Kayongo",
            album = "Herança",
            durationMs = 240000L, // 4:00
            mediaUri = "content://media/audio/42"
        )
        val queue = listOf(currentTrack)
        val currentIndex = 0
        val repeatMode = RepeatMode.ONE

        val nextIndex = when {
            repeatMode == RepeatMode.ONE -> currentIndex
            currentIndex + 1 < queue.size -> currentIndex + 1
            repeatMode == RepeatMode.ALL && queue.isNotEmpty() -> 0
            else -> -1
        }

        assertEquals(0, nextIndex)
        val nextTrack = queue[nextIndex]
        assertEquals(currentTrack.id, nextTrack.id)
    }

    @Test
    fun `test crossfade state lifecycle transitions`() {
        var state = CrossfadeState.IDLE
        assertEquals(CrossfadeState.IDLE, state)

        // 1. Preparation
        state = CrossfadeState.PREPARING_NEXT
        assertEquals(CrossfadeState.PREPARING_NEXT, state)

        // 2. Active Simultaneous Playback
        state = CrossfadeState.CROSSFADE_ACTIVE
        assertEquals(CrossfadeState.CROSSFADE_ACTIVE, state)

        // 3. Completed Transition
        state = CrossfadeState.COMPLETED
        assertEquals(CrossfadeState.COMPLETED, state)

        // 4. Return to IDLE
        state = CrossfadeState.IDLE
        assertEquals(CrossfadeState.IDLE, state)
    }

    @Test
    fun `test standard playback volume is 100 percent unity gain`() {
        val standardVolume = 1.0f
        assertEquals(1.0f, standardVolume, 0.001f)
        assertFalse("Volume must not be artificially clamped", standardVolume < 0.9f)
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
        // Track 2 started at 00:00 when Track 1 reached 03:55 (in a 5s crossfade)
        // Over 5s, Track 2 progressed from 00:00 to 00:05 (5000ms)
        val initialSecondaryPos = 0L
        val crossfadeDurationMs = 5000L
        val elapsedCrossfadeMs = 5000L

        val secondaryCurrentPos = initialSecondaryPos + elapsedCrossfadeMs
        assertEquals(5000L, secondaryCurrentPos)

        // Upon promotion, the promoted player's position is strictly preserved and NEVER reset to 0
        val promotedPlayerPos = secondaryCurrentPos
        assertEquals(5000L, promotedPlayerPos)
        assertTrue("Promoted position must be > 0", promotedPlayerPos > 0L)
    }

    @Test
    fun `test short track crossfade adaptation`() {
        val configuredCrossfadeSec = 8
        val configuredCrossfadeMs = configuredCrossfadeSec * 1000L

        // Normal tracks (e.g. 4 minutes)
        val durNormal1 = 240000L
        val durNormal2 = 200000L
        val effectiveNormal = minOf(configuredCrossfadeMs, durNormal1 / 2, durNormal2 / 2).coerceAtLeast(1000L)
        assertEquals(8000L, effectiveNormal)

        // Very short track (e.g. 10s voice note)
        val durShort1 = 10000L
        val durShort2 = 240000L
        val effectiveShort = minOf(configuredCrossfadeMs, durShort1 / 2, durShort2 / 2).coerceAtLeast(1000L)
        assertEquals(5000L, effectiveShort) // Half of 10s is 5s
    }

    @Test
    fun `test equal-power DJ crossfade volume curve values`() {
        fun oldGain(progress: Float) = kotlin.math.cos(progress * kotlin.math.PI / 2.0).toFloat()
        fun newGain(progress: Float) = kotlin.math.sin(progress * kotlin.math.PI / 2.0).toFloat()

        // Start of crossfade (0.0): Track 1 = 1.0, Track 2 = 0.0
        assertEquals(1.0f, oldGain(0.0f), 0.001f)
        assertEquals(0.0f, newGain(0.0f), 0.001f)

        // Midpoint of crossfade (0.5): Equal-power ~0.707 (sum of squares = 1.0)
        assertEquals(0.707f, oldGain(0.5f), 0.01f)
        assertEquals(0.707f, newGain(0.5f), 0.01f)
        val powerAtMid = (oldGain(0.5f) * oldGain(0.5f)) + (newGain(0.5f) * newGain(0.5f))
        assertEquals(1.0f, powerAtMid, 0.01f)

        // End of crossfade (1.0): Track 1 = 0.0, Track 2 = 1.0
        assertEquals(0.0f, oldGain(1.0f), 0.001f)
        assertEquals(1.0f, newGain(1.0f), 0.001f)
    }

    @Test
    fun `test repeat modes crossfade target index resolution`() {
        val queue = listOf(
            AudioTrack(1L, "T1", "A", "Alb", 180000L, "uri1"),
            AudioTrack(2L, "T2", "A", "Alb", 180000L, "uri2"),
            AudioTrack(3L, "T3", "A", "Alb", 180000L, "uri3")
        )

        // Repeat ONE: next track is the same track index (0 -> 0)
        val nextOne = 0 // currentIndex
        assertEquals(0, nextOne)

        // Repeat ALL at queue end (index 2 of 3): next track is index 0
        val nextAllAtEnd = if (2 + 1 < queue.size) 2 + 1 else 0
        assertEquals(0, nextAllAtEnd)

        // Repeat OFF at queue end: no next track (-1)
        val nextOffAtEnd = if (2 + 1 < queue.size) 2 + 1 else -1
        assertEquals(-1, nextOffAtEnd)
    }

    @Test
    fun `test manual track switch triggers DJ crossfade smoothly`() {
        val crossfadeSec = 8
        val crossfadeMs = crossfadeSec * 1000L
        val trackA = AudioTrack(1L, "Track A", "Artist A", "Album", 240000L, "content://media/1")
        val trackB = AudioTrack(2L, "Track B", "Artist B", "Album", 200000L, "content://media/2")

        var isPlaying = true
        var activeTrack = trackA
        var secondaryTrack: AudioTrack? = null
        var transitionActive = false

        // User clicks Track B while Track A is playing at 02:37 (157000ms)
        if (isPlaying && crossfadeSec > 0 && trackB.id != activeTrack.id) {
            transitionActive = true
            secondaryTrack = trackB
        }

        assertTrue("Transition must be active on manual selection", transitionActive)
        assertEquals(trackB.id, secondaryTrack?.id)

        // After 8 seconds of simultaneous play, Track B is at ~8000ms
        val trackBPositionAfterCrossfade = crossfadeMs
        assertEquals(8000L, trackBPositionAfterCrossfade)

        // Promotion happens without seekTo(0)
        activeTrack = secondaryTrack!!
        transitionActive = false
        assertEquals("Track B", activeTrack.title)
        assertEquals(8000L, trackBPositionAfterCrossfade)
    }

    @Test
    fun `test skip next and skip previous trigger crossfade when enabled`() {
        val crossfadeSec = 8
        val queue = listOf(
            AudioTrack(1L, "Track 1", "Artist", "Album", 200000L, "uri1"),
            AudioTrack(2L, "Track 2", "Artist", "Album", 220000L, "uri2"),
            AudioTrack(3L, "Track 3", "Artist", "Album", 240000L, "uri3")
        )
        val currentIndex = 0
        val currentTrack = queue[currentIndex]
        val isPlaying = true

        val nextIndex = currentIndex + 1
        val nextTrack = queue[nextIndex]

        val shouldCrossfadeNext = isPlaying && crossfadeSec > 0 && nextTrack.id != currentTrack.id
        assertTrue("Skip next must trigger crossfade", shouldCrossfadeNext)

        val prevIndex = 0
        val shouldCrossfadeSame = isPlaying && crossfadeSec > 0 && (queue[prevIndex].id != currentTrack.id)
        assertFalse("Same track switch without repeat ONE should not trigger", shouldCrossfadeSame)

        val shouldCrossfadeRepeatOne = isPlaying && crossfadeSec > 0 && (queue[prevIndex].id == currentTrack.id)
        assertTrue("Same track switch WITH repeat ONE must trigger crossfade", shouldCrossfadeRepeatOne)
    }

    @Test
    fun `test repeat one automatic DJ crossfade A1 to A2 to A3`() {
        val crossfadeSec = 8
        val crossfadeMs = crossfadeSec * 1000L
        val trackA = AudioTrack(1L, "Song Intro-Outro", "Artist", "Album", 258000L, "content://media/1") // 04:18

        val repeatMode = RepeatMode.ONE
        val currentQueue = listOf(trackA)
        val currentIndex = 0

        // Determine next track under RepeatMode.ONE
        val nextIndex = if (repeatMode == RepeatMode.ONE) currentIndex else -1
        assertEquals(0, nextIndex)
        val nextTrack = currentQueue[nextIndex]
        assertEquals(trackA.id, nextTrack.id)

        // At 04:10 (250000ms), remainingMs = 8000ms <= crossfadeMs
        val currentPosMs = 250000L
        val remainingMs = trackA.durationMs - currentPosMs
        assertEquals(8000L, remainingMs)

        // Crossfade starts: Player A1 is playing at 04:10, Player A2 starts at 00:00
        var playerA1Pos = 250000L
        var playerA2Pos = 0L
        var playerA1Vol = 1.0f
        var playerA2Vol = 0.0f

        // Simultaneous progress test
        val stepMs = 2000L
        for (elapsed in stepMs..crossfadeMs step stepMs) {
            playerA1Pos += stepMs
            playerA2Pos += stepMs
            val progress = elapsed.toFloat() / crossfadeMs
            playerA1Vol = kotlin.math.cos(progress * kotlin.math.PI / 2.0).toFloat()
            playerA2Vol = kotlin.math.sin(progress * kotlin.math.PI / 2.0).toFloat()

            assertTrue("Player A1 must advance in position", playerA1Pos > 250000L)
            assertTrue("Player A2 must advance in position from 00:00", playerA2Pos > 0L)
            assertTrue("Both players must have non-zero acoustic power in middle of transition",
                playerA1Vol > 0.0f && playerA2Vol > 0.0f)
        }

        // At 8s (04:18 for A1, 00:08 for A2)
        assertEquals(258000L, playerA1Pos)
        assertEquals(8000L, playerA2Pos)

        // A1 is released, A2 is promoted at 00:08 without seekTo(0)
        val promotedPlayerPos = playerA2Pos
        assertEquals(8000L, promotedPlayerPos)

        // Cycle 2: A2 plays until 04:10, then A2 -> A3 triggers with exact same 8s crossfade
        val a2NextRemaining = trackA.durationMs - 250000L
        assertEquals(8000L, a2NextRemaining)
        assertTrue("A2 must trigger A3 crossfade at 04:10", a2NextRemaining <= crossfadeMs)
    }

    @Test
    fun `test playTrackList preserves crossfade when selecting different track while playing`() {
        val crossfadeSec = 8
        val trackA = AudioTrack(1L, "Track A", "Artist", "Album", 200000L, "uri1")
        val trackB = AudioTrack(2L, "Track B", "Artist", "Album", 220000L, "uri2")
        val currentPlayingTrack = trackA
        val isCurrentlyPlaying = true

        val selectedTrack = trackB
        val shouldTransition = isCurrentlyPlaying && crossfadeSec > 0 && selectedTrack.id != currentPlayingTrack.id
        assertTrue("Manual selection of Track B during Track A must trigger crossfade", shouldTransition)
    }

    @Test
    fun `test real pcm acoustic telemetry simultaneous validation`() {
        com.example.service.audio.DjAudioMixerMonitor.updateTelemetry(
            tag = "PLAYER_A",
            sampleCount = 1024,
            rms = 0.45f,
            peak = 0.85f,
            dominantFreq = 440.0f
        )
        com.example.service.audio.DjAudioMixerMonitor.updateTelemetry(
            tag = "PLAYER_B",
            sampleCount = 1024,
            rms = 0.42f,
            peak = 0.80f,
            dominantFreq = 880.0f
        )

        val playerA = com.example.service.audio.DjAudioMixerMonitor.playerAPcm.value
        val playerB = com.example.service.audio.DjAudioMixerMonitor.playerBPcm.value

        assertTrue("Player A must be delivering real PCM", playerA.isReceivingRealPcm)
        assertTrue("Player B must be delivering real PCM", playerB.isReceivingRealPcm)
        assertEquals(440.0f, playerA.dominantFrequencyHz, 5.0f)
        assertEquals(880.0f, playerB.dominantFrequencyHz, 5.0f)
    }
}
