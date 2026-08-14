package com.example.lanman.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/**
 * Common easing curves for the Translucent Monolith design system.
 */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val StandardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

/**
 * Standard duration for sweeping light gradients, status transitions, etc.
 */
const val MotionDurationLong = 500
const val MotionDurationMedium = 300
const val MotionDurationShort = 150

/**
 * Reusable animation spec for elements entering/exiting or shifting layout.
 */
fun <T> defaultMonolithSpring() = androidx.compose.animation.core.spring<T>(
    dampingRatio = 0.8f,
    stiffness = 380f
)

/**
 * Reusable animation spec for generic tween fades.
 */
fun <T> monolithFadeTween() = tween<T>(
    durationMillis = MotionDurationMedium,
    easing = StandardEasing
)
