package com.example.scansense10

import android.graphics.RectF

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val rect: RectF
)

