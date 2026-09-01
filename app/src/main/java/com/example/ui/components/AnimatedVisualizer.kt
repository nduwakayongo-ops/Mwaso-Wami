package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.service.audio.RealtimeAudioState
import kotlin.math.sin

// Reference Color Palette
private val ColorWarmOrange = Color(0xFFF59E0B)
private val ColorCoral = Color(0xFFF97316)
private val ColorGold = Color(0xFFEAB308)
private val ColorNeonPink = Color(0xFFEC4899)
private val ColorMagenta = Color(0xFFD946EF)
private val ColorPurple = Color(0xFFA855F7)
private val ColorCyan = Color(0xFF06B6D4)
private val ColorElectricBlue = Color(0xFF38BDF8)
private val ColorNeonGreen = Color(0xFF22C55E)

/**
 * Real-time dynamic audio waves surrounding the central album cover.
 * Reproduces the visual design of the reference image with live PCM and FFT audio data.
 */
@Composable
fun RealtimeCoverWithAudioWaves(
    artworkUri: String?,
    audioState: RealtimeAudioState,
    isPlaying: Boolean,
    economyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val waveform = audioState.waveform
    val fftBands = audioState.fftBands
    val bass = if (isPlaying) audioState.bassEnergy else 0f
    val treble = if (isPlaying) audioState.trebleEnergy else 0f
    val amplitude = if (isPlaying) audioState.overallAmplitude else 0f
    val beatPulse = if (isPlaying && !economyMode) audioState.beatPulse else 1.0f

    // Reusable Path to avoid per-frame allocations
    val wavePathLeft = remember { Path() }
    val wavePathRight = remember { Path() }
    val wavePathCenter = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.05f),
        contentAlignment = Alignment.Center
    ) {
        // 1. Multi-Layered Real-time Glowing Waveforms and Frequency Bars Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerY = canvasH * 0.5f

            // Central box boundaries for waves to emerge from
            val boxW = canvasW * 0.52f
            val boxH = boxW
            val leftBoxEdge = (canvasW - boxW) / 2f
            val rightBoxEdge = leftBoxEdge + boxW
            val topBoxEdge = (canvasH - boxH) / 2f
            val bottomBoxEdge = topBoxEdge + boxH

            // Draw Background Ambient Glowing Aura responding to beat and amplitude
            if (isPlaying && !economyMode && amplitude > 0.02f) {
                val glowAlpha = (amplitude * 0.45f + (beatPulse - 1f) * 4f).coerceIn(0.08f, 0.55f)

                // Left amber glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ColorWarmOrange.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(leftBoxEdge * 0.5f, centerY),
                        radius = boxW * 0.65f
                    ),
                    center = Offset(leftBoxEdge * 0.5f, centerY),
                    radius = boxW * 0.65f
                )

                // Right cyan glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ColorCyan.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(rightBoxEdge + leftBoxEdge * 0.5f, centerY),
                        radius = boxW * 0.65f
                    ),
                    center = Offset(rightBoxEdge + leftBoxEdge * 0.5f, centerY),
                    radius = boxW * 0.65f
                )

                // Center magenta glow behind artwork
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ColorMagenta.copy(alpha = glowAlpha * 0.7f), Color.Transparent),
                        center = Offset(canvasW / 2f, centerY),
                        radius = boxW * 0.8f
                    ),
                    center = Offset(canvasW / 2f, centerY),
                    radius = boxW * 0.8f
                )
            }

            // ----------------------------------------------------
            // 2. Vertical Equalizer Frequency Bars (Left & Right)
            // ----------------------------------------------------
            val barCount = if (economyMode) 8 else 14
            val leftBarSpacing = leftBoxEdge / (barCount + 1)
            val rightBarSpacing = (canvasW - rightBoxEdge) / (barCount + 1)

            // Left Vertical Bars: Low-end Bass & Mids (Warm Orange / Gold)
            for (i in 0 until barCount) {
                val x = leftBoxEdge - (i + 1) * leftBarSpacing
                val fftIdx = (i * 2).coerceIn(0, fftBands.size - 1)
                val barVal = if (isPlaying) (fftBands[fftIdx] * 0.85f + bass * 0.45f).coerceIn(0.04f, 1f) else 0.04f
                val barH = canvasH * 0.48f * barVal * (if (isPlaying) 1f else 0.2f)
                val topY = centerY - barH / 2f

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ColorGold.copy(alpha = 0.9f),
                            ColorWarmOrange.copy(alpha = 0.75f),
                            ColorCoral.copy(alpha = 0.2f)
                        ),
                        startY = topY,
                        endY = topY + barH
                    ),
                    topLeft = Offset(x - 2.5f, topY),
                    size = Size(4.5f, barH),
                    cornerRadius = CornerRadius(2.25f, 2.25f)
                )
            }

            // Right Vertical Bars: High-Mids & Treble (Electric Cyan / Blue)
            for (i in 0 until barCount) {
                val x = rightBoxEdge + (i + 1) * rightBarSpacing
                val fftIdx = (24 + i * 2).coerceIn(0, fftBands.size - 1)
                val barVal = if (isPlaying) (fftBands[fftIdx] * 0.85f + treble * 0.45f).coerceIn(0.04f, 1f) else 0.04f
                val barH = canvasH * 0.48f * barVal * (if (isPlaying) 1f else 0.2f)
                val topY = centerY - barH / 2f

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ColorElectricBlue.copy(alpha = 0.9f),
                            ColorCyan.copy(alpha = 0.75f),
                            ColorPurple.copy(alpha = 0.2f)
                        ),
                        startY = topY,
                        endY = topY + barH
                    ),
                    topLeft = Offset(x - 2.5f, topY),
                    size = Size(4.5f, barH),
                    cornerRadius = CornerRadius(2.25f, 2.25f)
                )
            }

            // ----------------------------------------------------
            // 3. Real-Time Flowing Sine Wave Ribbons (Left, Center, Right)
            // ----------------------------------------------------
            val waveLayers = if (economyMode) 2 else 3

            for (layer in 0 until waveLayers) {
                val layerPhase = layer * 0.8f
                val layerScale = 1.0f - layer * 0.2f

                // --- LEFT WAVES (Warm Orange / Coral / Gold) ---
                wavePathLeft.reset()
                val leftStart = 0f
                val leftEnd = leftBoxEdge + 20f
                val leftPoints = 32
                val leftStep = (leftEnd - leftStart) / (leftPoints - 1)

                for (p in 0 until leftPoints) {
                    val x = leftStart + p * leftStep
                    val normalizedP = p.toFloat() / (leftPoints - 1)
                    val sampleIdx = (p).coerceIn(0, waveform.size - 1)
                    val rawSample = if (isPlaying) waveform[sampleIdx] else 0f

                    // Live modulation: PCM sample + Bass energy sine fluctuation
                    val waveAmp = if (isPlaying) {
                        (canvasH * 0.22f * (bass * 0.7f + amplitude * 0.5f + 0.08f) * layerScale)
                    } else 4f

                    val sineOffset = sin((normalizedP * 3.5f * Math.PI) + layerPhase).toFloat()
                    val y = centerY + (rawSample * 0.6f + sineOffset * 0.4f) * waveAmp

                    if (p == 0) wavePathLeft.moveTo(x, y) else wavePathLeft.lineTo(x, y)
                }

                drawPath(
                    path = wavePathLeft,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ColorWarmOrange.copy(alpha = 0.2f),
                            ColorCoral.copy(alpha = 0.9f * layerScale),
                            ColorGold.copy(alpha = 0.95f * layerScale)
                        ),
                        startX = leftStart,
                        endX = leftEnd
                    ),
                    style = Stroke(width = (4f - layer * 0.8f) * beatPulse, cap = StrokeCap.Round)
                )

                // --- RIGHT WAVES (Electric Cyan / Turquoise / Sky Blue) ---
                wavePathRight.reset()
                val rightStart = rightBoxEdge - 20f
                val rightEnd = canvasW
                val rightPoints = 32
                val rightStep = (rightEnd - rightStart) / (rightPoints - 1)

                for (p in 0 until rightPoints) {
                    val x = rightStart + p * rightStep
                    val normalizedP = p.toFloat() / (rightPoints - 1)
                    val sampleIdx = (32 + p).coerceIn(0, waveform.size - 1)
                    val rawSample = if (isPlaying) waveform[sampleIdx] else 0f

                    // Live modulation: PCM sample + Treble energy sine fluctuation
                    val waveAmp = if (isPlaying) {
                        (canvasH * 0.22f * (treble * 0.7f + amplitude * 0.5f + 0.08f) * layerScale)
                    } else 4f

                    val sineOffset = sin((normalizedP * 3.5f * Math.PI) - layerPhase).toFloat()
                    val y = centerY + (rawSample * 0.6f + sineOffset * 0.4f) * waveAmp

                    if (p == 0) wavePathRight.moveTo(x, y) else wavePathRight.lineTo(x, y)
                }

                drawPath(
                    path = wavePathRight,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ColorCyan.copy(alpha = 0.95f * layerScale),
                            ColorElectricBlue.copy(alpha = 0.85f * layerScale),
                            ColorCyan.copy(alpha = 0.2f)
                        ),
                        startX = rightStart,
                        endX = rightEnd
                    ),
                    style = Stroke(width = (4f - layer * 0.8f) * beatPulse, cap = StrokeCap.Round)
                )

                // --- CENTER/BOTTOM WAVES (Magenta / Purple) ---
                wavePathCenter.reset()
                val centerPoints = 40
                val centerStep = canvasW / (centerPoints - 1)

                for (p in 0 until centerPoints) {
                    val x = p * centerStep
                    val normalizedP = p.toFloat() / (centerPoints - 1)
                    val sampleIdx = (p).coerceIn(0, waveform.size - 1)
                    val rawSample = if (isPlaying) waveform[sampleIdx] else 0f

                    val midAmp = if (isPlaying) {
                        (canvasH * 0.18f * (audioState.midEnergy * 0.6f + amplitude * 0.4f + 0.06f) * layerScale)
                    } else 3f

                    val sineOffset = sin((normalizedP * 4.0f * Math.PI) + layerPhase * 1.5f).toFloat()
                    val y = centerY + (rawSample * 0.5f + sineOffset * 0.5f) * midAmp + (layer * 8f)

                    if (p == 0) wavePathCenter.moveTo(x, y) else wavePathCenter.lineTo(x, y)
                }

                drawPath(
                    path = wavePathCenter,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ColorWarmOrange.copy(alpha = 0.3f),
                            ColorMagenta.copy(alpha = 0.8f * layerScale),
                            ColorPurple.copy(alpha = 0.8f * layerScale),
                            ColorCyan.copy(alpha = 0.3f)
                        ),
                        startX = 0f,
                        endX = canvasW
                    ),
                    style = Stroke(width = (3.5f - layer * 0.8f) * beatPulse, cap = StrokeCap.Round)
                )
            }
        }

        // ----------------------------------------------------
        // 4. Central Album Artwork Card with Neon Border & Beat Pulse
        // ----------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize(0.58f)
                .graphicsLayer {
                    scaleX = beatPulse
                    scaleY = beatPulse
                }
                .shadow(
                    elevation = if (isPlaying) 28.dp else 12.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = ColorMagenta
                )
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ColorWarmOrange,
                            ColorMagenta,
                            ColorPurple,
                            ColorCyan
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkUri)
                        .size(400, 400)
                        .precision(coil.size.Precision.INEXACT)
                        .placeholder(R.drawable.ic_album_placeholder)
                        .error(R.drawable.ic_album_placeholder)
                        .crossfade(150)
                        .build(),
                    contentDescription = "Capa do Álbum",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_album_placeholder),
                    contentDescription = "Capa Padrão Mwaso Wami",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Real-time Multi-Colored Waveform Progress Bar / Seeker.
 * Features 48 vertical equalizer bars in horizontal color gradient
 * (Orange -> Yellow -> Magenta -> Purple -> Cyan) with an active green progress overlay.
 */
@Composable
fun RealtimeWaveformProgressBar(
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    formattedCurrent: String,
    formattedDuration: String,
    audioState: RealtimeAudioState,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val activeProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)
    val fftBands = audioState.fftBands

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("realtime_waveform_seekbar")
    ) {
        // Waveform Bar Canvas + Seeker Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (newProgress * durationMs).toLong()
                        onSeekTo(targetMs)
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetMs = (dragProgress * durationMs).toLong()
                            onSeekTo(targetMs)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalWidth = size.width
                val totalHeight = size.height
                val centerY = totalHeight / 2f
                val barCount = 48
                val barSpacing = 3.5f
                val totalSpacing = (barCount - 1) * barSpacing
                val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(3f)

                // 1. Draw Multi-Colored Equalizer Waveform Bars
                for (i in 0 until barCount) {
                    val x = i * (barWidth + barSpacing) + barWidth / 2f
                    val normalizedIndex = i.toFloat() / barCount
                    val fftIdx = i.coerceIn(0, fftBands.size - 1)

                    // Audio-driven bar height calculation
                    val liveFft = if (isPlaying) fftBands[fftIdx] else 0.05f
                    // Static baseline contour to make waveform look realistic even on low volume
                    val baseContour = 0.25f + 0.55f * sin(normalizedIndex * Math.PI).toFloat()
                    val dynamicFactor = (baseContour * 0.4f + liveFft * 0.6f).coerceIn(0.12f, 0.98f)

                    val barHeight = totalHeight * 0.82f * dynamicFactor
                    val topY = centerY - barHeight / 2f

                    // Rainbow / Multi-Color Palette matching reference image:
                    // Left (Orange/Gold) -> Center (Pink/Magenta) -> Right (Purple/Cyan)
                    val barColor = when {
                        normalizedIndex < 0.20f -> ColorWarmOrange
                        normalizedIndex < 0.40f -> ColorGold
                        normalizedIndex < 0.60f -> ColorNeonPink
                        normalizedIndex < 0.80f -> ColorPurple
                        else -> ColorCyan
                    }

                    val isPastProgress = (x / totalWidth) <= activeProgress
                    val alpha = if (isPastProgress) 0.95f else 0.45f

                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x - barWidth / 2f, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // 2. Draw Green Progress Seeker Line & Indicator Circle
                val progressX = totalWidth * activeProgress
                val lineY = totalHeight - 6.dp.toPx()

                // Background inactive track
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, lineY),
                    end = Offset(totalWidth, lineY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active glowing green track
                drawLine(
                    color = ColorNeonGreen,
                    start = Offset(0f, lineY),
                    end = Offset(progressX, lineY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Green Thumb Dot
                drawCircle(
                    color = ColorNeonGreen,
                    radius = if (isDragging) 7.dp.toPx() else 5.5.dp.toPx(),
                    center = Offset(progressX, lineY)
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 3.5.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(progressX, lineY)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Timestamps (e.g. 2:18 and 4:35)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayedCurrent = if (isDragging) {
                val dragMs = (dragProgress * durationMs).toLong()
                val sec = (dragMs / 1000).coerceAtLeast(0)
                String.format("%d:%02d", sec / 60, sec % 60)
            } else formattedCurrent

            Text(
                text = displayedCurrent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
        }
    }
}

/**
 * Mini audio waveform visualizer for the MiniPlayerBar.
 */
@Composable
fun RealtimeMiniVisualizer(
    audioState: RealtimeAudioState,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16
) {
    val fftBands = audioState.fftBands

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val totalW = size.width
        val totalH = size.height
        val barSpacing = 2.5f
        val totalSpacing = (barCount - 1) * barSpacing
        val barW = ((totalW - totalSpacing) / barCount).coerceAtLeast(2f)

        for (i in 0 until barCount) {
            val x = i * (barW + barSpacing)
            val fftIdx = (i * 2).coerceIn(0, fftBands.size - 1)
            val liveVal = if (isPlaying) fftBands[fftIdx].coerceIn(0.08f, 1f) else 0.08f
            val barH = totalH * liveVal
            val y = totalH - barH

            val color = when {
                i < barCount * 0.33f -> ColorWarmOrange
                i < barCount * 0.66f -> ColorMagenta
                else -> ColorCyan
            }

            drawRoundRect(
                color = color.copy(alpha = if (isPlaying) 0.85f else 0.3f),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
