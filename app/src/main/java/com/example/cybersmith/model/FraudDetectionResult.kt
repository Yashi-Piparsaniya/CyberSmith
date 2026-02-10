package com.example.cybersmith.model

import kotlinx.serialization.Serializable

@Serializable
data class FraudDetectionResult(
    val isFraud: Boolean,
    val confidence: Float = 0f,
    val reason: String? = null
)
