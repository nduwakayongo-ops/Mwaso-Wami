package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.TerracottaAccent
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotatingVinylCover(
    artworkUri: String?,
    isPlaying: Boolean,
    economyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_animation")

    // Rotation animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (economyMode) 12000 else 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Musical beat pulse animation
    val beatPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beatPulse"
    )

    // Wave ring expansion animation
    val ringPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPhase"
    )

    // Particle angle phase
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    val currentScale = if (isPlaying && !economyMode) beatPulse else 1.0f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic Musical Waves & Ripple Aura in the background
        if (isPlaying && !economyMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.minDimension / 2

                // 3 expanding audio ripple wave rings
                for (r in 0..2) {
                    val p = (ringPhase + r * 0.333f) % 1f
                    val currentRadius = maxRadius * (0.68f + p * 0.32f)
                    val alpha = ((1f - p) * 0.45f).coerceIn(0f, 1f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AmberPrimary.copy(alpha = alpha),
                                GoldAccent.copy(alpha = alpha * 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = currentRadius
                        ),
                        radius = currentRadius,
                        center = center,
                        style = Stroke(width = 2.5f * (1f - p * 0.5f))
                    )
                }

                // Orbiting glowing musical frequency sparks
                val numParticles = 8
                for (i in 0 until numParticles) {
                    val angle = particlePhase + (i * Math.PI * 2 / numParticles).toFloat()
                    val dist = maxRadius * (0.82f + 0.08f * sin(angle * 3f))
                    val px = center.x + dist * cos(angle)
                    val py = center.y + dist * sin(angle)
                    val particleAlpha = 0.5f + 0.5f * sin(angle * 2f)

                    drawCircle(
                        color = if (i % 2 == 0) GoldAccent.copy(alpha = particleAlpha) else AmberPrimary.copy(alpha = particleAlpha),
                        radius = 3.5f,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Outer glowing vinyl disc chassis
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            Color(0xFF16100A),
                            ObsidianDark
                        )
                    )
                )
                .border(2.5.dp, Brush.linearGradient(listOf(AmberPrimary, GoldAccent, TerracottaAccent)), CircleShape)
                .shadow(elevation = if (isPlaying) 20.dp else 10.dp, shape = CircleShape)
        )

        // Vinyl Grooves
        Canvas(modifier = Modifier.fillMaxSize(0.92f).padding(12.dp)) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            val ringCount = 9
            for (i in 1..ringCount) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f + (i % 2) * 0.035f),
                    radius = radius * (0.42f + i * 0.058f),
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }
        }

        // Center Album Artwork Disc (Spinning with GPU acceleration)
        Box(
            modifier = Modifier
                .fillMaxSize(0.66f)
                .graphicsLayer {
                    rotationZ = if (isPlaying) rotationAngle else 0f
                    scaleX = currentScale
                    scaleY = currentScale
                }
                .clip(CircleShape)
                .border(3.dp, AmberPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkUri)
                        .size(300, 300)
                        .precision(coil.size.Precision.INEXACT)
                        .placeholder(R.drawable.ic_album_placeholder)
                        .error(R.drawable.ic_album_placeholder)
                        .crossfade(100)
                        .build(),
                    contentDescription = "Capa da Música",
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

            // Center spindle hole
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ObsidianDark)
                    .border(2.5.dp, GoldAccent, CircleShape)
            )
        }
    }
}

@Composable
fun FrequencyVisualizerBars(
    isPlaying: Boolean,
    economyMode: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24
) {
    if (economyMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AmberPrimary.copy(alpha = if (isPlaying) 0.9f else 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "freq_bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
    ) {
        val width = size.width
        val height = size.height
        val totalSpacing = (barCount - 1) * 4f
        val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(3f)

        for (i in 0 until barCount) {
            val x = i * (barWidth + 4f)
            val factor = if (isPlaying) {
                val wave1 = sin(phase + i * 0.45f)
                val wave2 = sin(phase * 1.5f + i * 0.8f)
                val base = 0.2f + 0.75f * ((wave1 + wave2 + 2f) / 4f)
                base.coerceIn(0.12f, 0.98f)
            } else {
                0.08f
            }

            val barHeight = height * factor
            val y = height - barHeight

            val brush = Brush.verticalGradient(
                colors = listOf(
                    GoldAccent,
                    AmberPrimary,
                    TerracottaAccent
                ),
                startY = y,
                endY = height
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun SoundWavesGlow(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sound_waves")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val currentScale = if (isPlaying) pulse else 1.0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AmberPrimary.copy(alpha = if (isPlaying) 0.18f * currentScale else 0.05f),
                        TerracottaAccent.copy(alpha = if (isPlaying) 0.10f * currentScale else 0.02f),
                        Color.Transparent
                    )
                )
            )
    )
}
