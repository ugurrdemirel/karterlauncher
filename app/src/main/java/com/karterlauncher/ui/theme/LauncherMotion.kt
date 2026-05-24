package com.karterlauncher.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

object LauncherMotion {
    const val PanelFadeMs = 280
    const val PanelSlideMs = 320
    const val ColorFadeMs = 240
    const val GaugeSpringStiffness = 280f
    const val GaugeSpringDamping = 0.78f

    val SoftSpringFloat: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val GaugeSpring: AnimationSpec<Float> = spring(
        dampingRatio = GaugeSpringDamping,
        stiffness = GaugeSpringStiffness,
    )

    val SoftTweenFloat: AnimationSpec<Float> = tween(
        durationMillis = ColorFadeMs,
        easing = FastOutSlowInEasing,
    )

    fun panelContentTransform(): ContentTransform =
        fadeIn(tween(PanelFadeMs, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(PanelSlideMs, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> fullHeight / 10 },
            ) togetherWith
            fadeOut(tween(PanelFadeMs / 2, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                animationSpec = tween(PanelSlideMs / 2, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> -fullHeight / 14 },
            )

    fun tabContentTransform(): ContentTransform =
        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(160, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 0.98f, animationSpec = tween(160, easing = FastOutSlowInEasing))

    fun rootRevealTransition(): ContentTransform =
        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(180, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(180, easing = FastOutSlowInEasing))
}

fun Modifier.softPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = LauncherMotion.SoftSpringFloat,
        label = "softPressScale",
    )
    scale(scale)
}

@Composable
fun rememberSoftPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
